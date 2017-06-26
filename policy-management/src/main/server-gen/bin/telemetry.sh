#! /bin/bash

###
# ============LICENSE_START=======================================================
# ONAP POLICY
# ================================================================================
# Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
# ================================================================================
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# 
#      http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ============LICENSE_END=========================================================
##

REST_TOOL="http-prompt"
TELEMETRY_SPEC="${POLICY_HOME}/config/telemetry-spec.json"

if ! type -p "${REST_TOOL}" > /dev/null 2>&1; then
	echo "error: prerequisite software not found: http-prompt"
	exit 1
fi

if ! "${POLICY_HOME}"/bin/policy-management-controller status > /dev/null 2>&1; then
	echo "error: pdp-d is not running"
	exit 2
fi

if [[ ! -r ${TELEMETRY_SPEC} ]]; then
	echo "generating new spec .."
	if ! http -a "${ENGINE_MANAGEMENT_USER}:${ENGINE_MANAGEMENT_PASSWORD}" :9696/swagger.json > ${TELEMETRY_SPEC} 2> /dev/null; then
		echo "error: cannot generate telemetry spec"
		exit 3
	fi
fi

exec http-prompt http://localhost:9696/policy/pdp/engine --auth "${ENGINE_MANAGEMENT_USER}:${ENGINE_MANAGEMENT_PASSWORD}" --spec ${TELEMETRY_SPEC}
