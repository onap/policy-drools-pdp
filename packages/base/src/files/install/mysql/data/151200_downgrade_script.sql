/*-
 * ============LICENSE_START=======================================================
 * Base Package
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */

set foreign_key_checks=0;

DROP TABLE IF EXISTS xacml.PolicyEntity;
DROP TABLE IF EXISTS xacml.ConfigurationDataEntity;
DROP TABLE IF EXISTS xacml.PolicyDBDaoEntity;
DROP TABLE IF EXISTS xacml.GroupEntity;
DROP TABLE IF EXISTS xacml.PdpEntity;
DROP TABLE IF EXISTS xacml.ActionBodyEntity;
DROP TABLE IF EXISTS xacml.DatabaseLockEntity;
DROP TABLE IF EXISTS xacml.PolicyGroupEntity;

ALTER TABLE XACML.TERM DROP COLUMN FROMZONE;
ALTER TABLE XACML.TERM DROP COLUMN TOZONE;
  
ALTER TABLE XACML.ACTIONPOLICYDICT DROP INDEX ACTIONPOLICYDICT_UNIQUE;

DROP TABLE IF EXISTS XACML.ZONE;

DROP TABLE IF EXISTS XACML.POLICYVERSION;

ALTER TABLE XACML.VSCLACTION DROP INDEX VSCLACTION_VSCL_ACTION_UNIQUE;

set foreign_key_checks=1;
