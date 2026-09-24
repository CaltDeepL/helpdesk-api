-- ---------------------------------------------------------------------------
-- V1: 土台。users テーブルのみを作成する。
--     tickets 以降は実装ロードマップのタスク #4 以降で追加する。
--
-- 方針:
--   - 主キーは UUID v7 相当の生成をアプリ側で行う（時系列に近く索引効率が良い）
--   - 日時は timestamptz（UTC 保存）
--   - updated_at はトリガで自動更新し、UPDATE 文に書かせない
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id              UUID         PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT users_role_valid        CHECK (role IN ('AGENT', 'MANAGER')),
    CONSTRAINT users_email_not_blank   CHECK (btrim(email) <> ''),
    CONSTRAINT users_name_not_blank    CHECK (btrim(display_name) <> '')
);

-- 大文字小文字を区別せずメールアドレスを一意にする
CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email));

-- updated_at の自動更新
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();