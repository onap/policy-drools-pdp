#!/bin/bash

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
