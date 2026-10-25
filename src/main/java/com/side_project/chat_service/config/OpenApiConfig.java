package com.side_project.chat_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    /**
     * Project khong co unit test, nen cach kiem chung phai song o day - canh code,
     * mot nguon duy nhat. Swagger UI render markdown nen khoi curl hien dung.
     */
    private static final String DESCRIPTION = """
            REST API cua chat-service.

            ### Header
            | Header | Bat buoc | Y nghia |
            |---|---|---|
            | `X-User-Id` | Co | Danh tinh nguoi goi. Production do gateway chen va strip header tu ben ngoai; giai doan nay CHUA co auth nen client tu dat khi test. |
            | `X-Trace-Id` | Khong | Theo dau mot luong qua nhieu service. Khong gui thi server tu sinh. |

            ### Quy uoc ma loi
            | Ma | Khi nao |
            |---|---|
            | 404 | Khong phai thanh vien **hoac** hoi thoai khong ton tai - co y khong phan biet, de nguoi ngoai khong biet hoi thoai co that |
            | 403 | La thanh vien nhung thieu vai tro |
            | 409 | Xung dot nghiep vu |
            | 422 | Body sai |

            Body loi la `ProblemDetail`, co them field `code` (ten `ErrorCode`).

            ### Nghia vu cua client (khong phai tuy chon)
            1. Sap tin theo `seq`, khong theo thu tu frame den.
            2. Khu trung theo `messageId`. Nhan hai lan cung mot tin la binh thuong.
            3. Tin cua chinh minh ve hai lan (response 201 + WebSocket) - khop bang `clientMessageId`.
            4. Thay ho `seq` thi tu goi `?afterSeq=`.
            5. Sau reconnect, lay bu bang REST. Server khong tu phat lai.

            ### Kiem chung bang tay
            `<U1>`, `<U2>`, `<U3>` la `id` tra ve tu `POST /api/v1/users` (tao truoc, FK bat buoc).
            Mo DM hai lan - lan dau 201, lan sau 200, cung `conversationId`:
            ```
            curl -i -X POST localhost:8080/api/v1/conversations \\
              -H 'Content-Type: application/json' -H 'X-User-Id: <U1>' \\
              -d '{"type":"DM","otherUserId":"<U2>"}'
            ```
            Goi song song - phai chi ra mot hang:
            ```
            for i in 1 2 3 4 5; do (curl -s -o /dev/null -X POST localhost:8080/api/v1/conversations \\
              -H 'Content-Type: application/json' -H 'X-User-Id: <U1>' \\
              -d '{"type":"DM","otherUserId":"<U3>"}' &) ; done
            ```
            ```sql
            SELECT dm_key, count(*) FROM conversation WHERE type='DM' GROUP BY dm_key HAVING count(*) > 1;
            -- phai khong co dong nao
            ```
            Rang buoc DB phai chan (moi cau duoi day phai LOI):
            ```sql
            INSERT INTO conversation (type, dm_key, created_by) VALUES ('DM','<U1>:<U2>','<U1>');  -- trung dm_key
            INSERT INTO conversation (type, dm_key, created_by) VALUES ('GROUP','x:y','<U1>');   -- GROUP co dm_key
            UPDATE conversation_member SET last_read_seq = last_delivered_seq + 1;              -- da doc > da nhan
            ```

            ### WebSocket (CHUA hien thuc fan-out)
            Endpoint `/ws/chat` (STOMP), subscribe `/user/queue/chat`. Mot chieu: client
            KHONG gui gi qua WebSocket, gui tin bang REST. Event: `MESSAGE_CREATED`,
            `READ_UPDATED`, `MEMBERSHIP_CHANGED`, phan biet bang field `type`.
            """;

    @Bean
    public OpenAPI chatServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("chat-service API")
                .version("v1")
                .description(DESCRIPTION));
    }
}
