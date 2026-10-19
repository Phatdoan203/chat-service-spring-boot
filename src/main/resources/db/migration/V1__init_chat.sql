-- V1: schema khoi tao cho chat-service
--
-- Pham vi: chat 1-1 (nhan tu do), chat nhom + them/xoa thanh vien, trang thai
-- SENT / DELIVERED / READ, danh sach hoi thoai + tin cuoi + unread, phan trang lich su.
-- Chua co: xac thuc/phan quyen (coi nhu ai cung full quyen), dinh kem file, thu hoi tin.
--
-- Quyet dinh nen tang: thu tu tin do cot conversation.last_seq cap, bang
-- UPDATE ... RETURNING trong transaction. Xem .claude/specs/adr/0005.

CREATE TABLE conversation (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    type            varchar(8)   NOT NULL,
    -- Chi dung cho DM: least(userA,userB) || ':' || greatest(userA,userB).
    -- Chong tao trung hoi thoai 1-1 bang UNIQUE, khong can khoa o tang ung dung.
    dm_key          varchar(140),
    title           varchar(200),
    created_by      varchar(64)  NOT NULL,
    -- Bo dem cap seq cho tin trong hoi thoai nay. Khong bao gio giam.
    last_seq        bigint       NOT NULL DEFAULT 0,
    -- Con tro tin cuoi, cap nhat trong cung transaction gui tin.
    -- Co y KHONG dat FK sang message: no tao vong FK hai chieu, lam fixture va
    -- viec xoa du lieu test phuc tap, trong khi day chi la gia tri phi chuan hoa.
    last_message_id uuid,
    last_message_at timestamptz,
    created_at      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT conversation_type_chk
        CHECK (type IN ('DM', 'GROUP')),
    -- DM buoc phai co dm_key; GROUP buoc phai khong co.
    CONSTRAINT conversation_dm_key_chk
        CHECK ((type = 'DM') = (dm_key IS NOT NULL)),
    CONSTRAINT conversation_last_seq_chk
        CHECK (last_seq >= 0)
);

CREATE UNIQUE INDEX conversation_dm_key_uq ON conversation (dm_key);


CREATE TABLE conversation_member (
    conversation_id    uuid        NOT NULL REFERENCES conversation (id),
    user_id            varchar(64) NOT NULL,
    role               varchar(8)  NOT NULL DEFAULT 'MEMBER',
    status             varchar(8)  NOT NULL DEFAULT 'ACTIVE',
    -- conversation.last_seq tai thoi diem tham gia: cat lich su truoc do.
    join_seq           bigint      NOT NULL,
    -- Hai con tro, ca hai chi tang. DELIVERED va READ duoc suy ra tu day,
    -- nen bang message KHONG co cot status (xem ghi chu cuoi file).
    last_delivered_seq bigint      NOT NULL DEFAULT 0,
    last_read_seq      bigint      NOT NULL DEFAULT 0,
    joined_at          timestamptz NOT NULL DEFAULT now(),
    left_at            timestamptz,

    PRIMARY KEY (conversation_id, user_id),

    CONSTRAINT conversation_member_role_chk
        CHECK (role IN ('OWNER', 'MEMBER')),
    CONSTRAINT conversation_member_status_chk
        CHECK (status IN ('ACTIVE', 'LEFT', 'REMOVED')),
    CONSTRAINT conversation_member_left_at_chk
        CHECK ((status = 'ACTIVE') = (left_at IS NULL)),
    -- Da doc thi tat nhien da nhan. Khi cap nhat read phai keo delivered len cung:
    --   last_delivered_seq = GREATEST(last_delivered_seq, :seq)
    CONSTRAINT conversation_member_cursor_chk
        CHECK (last_read_seq <= last_delivered_seq),
    CONSTRAINT conversation_member_seq_chk
        CHECK (join_seq >= 0 AND last_delivered_seq >= 0)
);

-- Cho cau "danh sach hoi thoai cua toi".
CREATE INDEX conversation_member_user_idx ON conversation_member (user_id, status);


CREATE TABLE message (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id   uuid        NOT NULL REFERENCES conversation (id),
    -- Lien tuc, khong ho, trong pham vi mot hoi thoai. Client dua vao tinh chat
    -- nay de phat hien minh thieu tin.
    seq               bigint      NOT NULL,
    sender_id         varchar(64) NOT NULL,
    type              varchar(16) NOT NULL DEFAULT 'TEXT',
    body              text,
    attachment_id     varchar(64),
    -- Do client sinh, giu nguyen qua moi lan retry.
    client_message_id varchar(64) NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    deleted_at        timestamptz,

    CONSTRAINT message_seq_chk
        CHECK (seq > 0),
    CONSTRAINT message_type_chk
        CHECK (type IN ('TEXT', 'IMAGE', 'FILE')),
    -- Tin TEXT phai co noi dung thuc su, khong phai chuoi trang.
    CONSTRAINT message_text_body_chk
        CHECK (type <> 'TEXT' OR (body IS NOT NULL AND btrim(body) <> '')),
    CONSTRAINT message_attachment_chk
        CHECK (type = 'TEXT' OR attachment_id IS NOT NULL),
    CONSTRAINT message_body_len_chk
        CHECK (body IS NULL OR char_length(body) <= 4000)
);

-- Luoi an toan cho viec cap seq: logic sai thi DB chan ngay, khong am tham ghi sai.
-- Index nay cung phuc vu moi truy van lich su: Postgres quet btree nguoc duoc,
-- nen KHONG can them index (conversation_id, seq DESC).
CREATE UNIQUE INDEX message_conversation_seq_uq ON message (conversation_id, seq);

-- Chong luu trung khi nguoi gui retry vi mat response.
CREATE UNIQUE INDEX message_idempotency_uq
    ON message (conversation_id, sender_id, client_message_id);


CREATE TABLE outbox_event (
    -- bigserial, khong phai uuid: o day can thu tu phat.
    id             bigserial   PRIMARY KEY,
    aggregate_type varchar(32) NOT NULL DEFAULT 'CONVERSATION',
    aggregate_id   uuid        NOT NULL,
    event_type     varchar(40) NOT NULL,
    payload        jsonb       NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    published_at   timestamptz,
    attempts       int         NOT NULL DEFAULT 0,

    CONSTRAINT outbox_event_attempts_chk CHECK (attempts >= 0)
);

-- Partial index: chi chua dong chua publish nen luon nho, du bang co hang trieu dong.
CREATE INDEX outbox_event_unpublished_idx
    ON outbox_event (id) WHERE published_at IS NULL;


-- ---------------------------------------------------------------------------
-- Ghi chu: vi sao bang message KHONG co cot status
--
-- SENT      = dong message ton tai (da commit, da co seq).
-- DELIVERED = MIN(last_delivered_seq) cua cac thanh vien ACTIVE khac nguoi gui >= seq
-- READ      = MIN(last_read_seq)      cua cac thanh vien ACTIVE khac nguoi gui >= seq
--
-- Luu trang thai thanh cot tren message se bat buoc UPDATE mot dong message cho moi
-- nguoi nhan cua moi tin (nhom 8 nguoi, 33 tin/s -> 264 UPDATE/s), va sinh ra hai
-- nguon su that co the lech nhau. Hai con tro tren conversation_member cho cung
-- thong tin do voi chi phi O(1) khi gui va O(so thanh vien) khi doc.
--
-- Gioi han da biet: con tro chi tra loi "da nhan toi seq X", khong tra loi
-- "thiet bi nao da nhan". Khi nao can per-device thi them bang rieng, khong sua bang nay.
-- ---------------------------------------------------------------------------
