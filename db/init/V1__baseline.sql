CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    createdAt TIMESTAMP,
    updatedAt TIMESTAMP,
    version BIGINT,
    username VARCHAR(255) NOT NULL UNIQUE,
    passwordHash VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role VARCHAR(64) NOT NULL,
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS suppliers (
    id BIGSERIAL PRIMARY KEY,
    createdAt TIMESTAMP,
    updatedAt TIMESTAMP,
    version BIGINT,
    name VARCHAR(255) NOT NULL UNIQUE,
    reliabilityScore DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS contracts (
    id BIGSERIAL PRIMARY KEY,
    createdAt TIMESTAMP,
    updatedAt TIMESTAMP,
    version BIGINT,
    contractNumber VARCHAR(255) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL,
    dueDate DATE NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    CONSTRAINT fk_contract_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
);

CREATE TABLE IF NOT EXISTS shipments (
    id BIGSERIAL PRIMARY KEY,
    createdAt TIMESTAMP,
    updatedAt TIMESTAMP,
    version BIGINT,
    contract_id BIGINT NOT NULL,
    status VARCHAR(64) NOT NULL,
    plannedDate DATE NOT NULL,
    actualDate DATE,
    CONSTRAINT fk_shipment_contract FOREIGN KEY (contract_id) REFERENCES contracts(id)
);

CREATE TABLE IF NOT EXISTS risk_assessments (
    id BIGSERIAL PRIMARY KEY,
    createdAt TIMESTAMP,
    updatedAt TIMESTAMP,
    version BIGINT,
    contract_id BIGINT NOT NULL,
    riskScore DOUBLE PRECISION NOT NULL,
    riskLevel VARCHAR(32) NOT NULL,
    CONSTRAINT fk_risk_contract FOREIGN KEY (contract_id) REFERENCES contracts(id)
);

CREATE TABLE IF NOT EXISTS incidents (
    id BIGSERIAL PRIMARY KEY,
    createdAt TIMESTAMP,
    updatedAt TIMESTAMP,
    version BIGINT,
    shipment_id BIGINT NOT NULL,
    severity VARCHAR(32) NOT NULL,
    description VARCHAR(1024) NOT NULL,
    escalated BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_incident_shipment FOREIGN KEY (shipment_id) REFERENCES shipments(id)
);
