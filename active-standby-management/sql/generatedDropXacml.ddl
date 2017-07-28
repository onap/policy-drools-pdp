DROP TABLE StateManagementEntity
DROP TABLE ForwardProgressEntity
DROP TABLE ResourceRegistrationEntity
DROP TABLE IntegrityAuditEntity
DELETE FROM SEQUENCE WHERE SEQ_NAME = 'SEQ_GEN'
