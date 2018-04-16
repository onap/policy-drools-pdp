#!/bin/bash
###
# ============LICENSE_START=======================================================
# 
# ================================================================================
# Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

# skip installation if build.info file is present (restarting an existing container)
if [[ -f ${POLICY_HOME}/etc/build.info ]]; then
	echo "Found existing installation, will not reinstall"
	. ${POLICY_HOME}/etc/profile.d/env.sh
else 
	echo "installing .."

	# replace conf files from installer with environment-specific files
	# mounted from the hosting VM
	if [[ -d config ]]; then
		cp config/*.conf .
	fi

	# wait for nexus up before installing, since installation
	# needs to deploy some artifacts to the repo
	./wait-for-port.sh nexus 8081

	# remove broken symbolic links if any in data directory
	if [[ -d ${POLICY_HOME}/config ]]; then
		echo "removing dangling symbolic links"
		find -L ${POLICY_HOME}/config -type l -exec rm -- {} +
	fi

	apps=$(ls config/apps*.zip 2> /dev/null)

	echo "Applications found: ${apps}"

	if [[ -n ${apps} ]]; then
	    unzip -o ${apps}
	fi

	echo "docker install at ${PWD}"

	./docker-install.sh

	. /opt/app/policy/etc/profile.d/env.sh

	# install policy keystore

	mkdir -p ${POLICY_HOME}/etc/ssl
	cp config/policy-keystore ${POLICY_HOME}/etc/ssl

	if [[ -x config/drools-tweaks.sh ]] ; then
		echo "Executing tweaks"
		# file may not be executable; running it as an
		# argument to bash avoids needing execute perms.
		bash config/drools-tweaks.sh
	fi
fi

echo "Starting processes"

policy start

tail -f /dev/null
