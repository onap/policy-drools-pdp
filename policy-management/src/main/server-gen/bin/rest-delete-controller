#! /bin/bash

###
# ============LICENSE_START=======================================================
# policy-management
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
###

source $POLICY_HOME/etc/profile.d/env.sh

if [[ -n $1 ]]; then
	if [[ -n ${ENGINE_MANAGEMENT_PASSWORD} ]]; then
		curl --silent --user ${ENGINE_MANAGEMENT_USER}:${ENGINE_MANAGEMENT_PASSWORD} -X DELETE --header "Content-Type: application/json" \
			http://localhost:${ENGINE_MANAGEMENT_PORT}/policy/pdp/engine/controllers/${1}
	else
		curl --silent -X DELETE --header "Content-Type: application/json" \
			http://localhost:${ENGINE_MANAGEMENT_PORT}/policy/pdp/engine/controllers/${1}
	fi
	echo
	exit	
fi


		
cat <<-'EOF'

Usage: rest-delete-controller.sh closed-loop-sample|reporter|sepc|vsegw|.. (or any other controller idenfied by name)

EOF
