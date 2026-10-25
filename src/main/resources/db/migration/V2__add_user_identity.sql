CREATE TABLE app_user (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    username     varchar(64)  NOT NULL UNIQUE,
    display_name varchar(100) NOT NULL,
    email        varchar(254) UNIQUE,
    avatar_url   varchar(512),
    created_at   timestamptz  NOT NULL DEFAULT now()
);

-- Seed tu id dang co (varchar chua chuoi UUID hop le) de them duoc FK.
INSERT INTO app_user (id, username, display_name)
SELECT uid::uuid, uid, uid FROM (
    SELECT user_id AS uid FROM conversation_member
    UNION SELECT sender_id FROM message
    UNION SELECT created_by FROM conversation
) s WHERE uid IS NOT NULL
ON CONFLICT (id) DO NOTHING;

ALTER TABLE conversation_member ALTER COLUMN user_id    TYPE uuid USING user_id::uuid;
ALTER TABLE message             ALTER COLUMN sender_id  TYPE uuid USING sender_id::uuid;
ALTER TABLE conversation        ALTER COLUMN created_by TYPE uuid USING created_by::uuid;

-- dm_key khong tinh lai: da la least:greatest cua chuoi UUID, DmKeys van sap theo toString().

ALTER TABLE conversation_member ADD CONSTRAINT conversation_member_user_fk FOREIGN KEY (user_id)    REFERENCES app_user(id);
ALTER TABLE conversation        ADD CONSTRAINT conversation_created_by_fk  FOREIGN KEY (created_by) REFERENCES app_user(id);
ALTER TABLE message             ADD CONSTRAINT message_sender_fk           FOREIGN KEY (sender_id)  REFERENCES app_user(id);
