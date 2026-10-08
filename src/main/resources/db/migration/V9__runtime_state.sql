-- 서버가 꺼져도 남아야 하는 진행 중 상태 (0.8.0): 공성 · 필드 보스의 체력 · 피해 기록 · 손질 버프 · 시련 · 비기 동료 · 던전 진행.
-- scope 마다 key 하나에 한 줄, data 는 key=value;… (RuntimeStateService). expires_at 이 지나면 지운다 (0 = 끝없음).
CREATE TABLE runtime_state (
    scope       TEXT NOT NULL,
    key         TEXT NOT NULL,
    data        TEXT NOT NULL,
    expires_at  INTEGER NOT NULL DEFAULT 0,
    updated_at  INTEGER NOT NULL,
    PRIMARY KEY (scope, key)
);
CREATE INDEX runtime_state_expiry ON runtime_state (expires_at);
