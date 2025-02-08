CREATE EXTENSION IF NOT EXISTS pg_bigm;
CREATE INDEX IF NOT EXISTS idx_chatroom_name ON accord_chatroom USING GIN(name gin_bigm_ops);
CREATE INDEX IF NOT EXISTS idx_chatrecord_message ON chat_record USING GIN(message gin_bigm_ops);
CREATE INDEX IF NOT EXISTS idx_chatrecord_attachments ON chat_record USING GIN(attachments gin_bigm_ops);
CREATE INDEX IF NOT EXISTS idx_user_nickname ON accord_user USING GIN(nickname gin_bigm_ops);
CREATE INDEX IF NOT EXISTS idx_user_username ON accord_user USING GIN(username gin_bigm_ops);

