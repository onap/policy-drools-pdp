#!/bin/bash

# skip installation if build.info file is present (restarting an existing container)
if [[ -f /opt/app/policy/etc/build.info ]]; then
	echo "Found existing installation, will not reinstall"
	. /opt/app/policy/etc/profile.d/env.sh
else 
	# replace conf files from installer with environment-specific files
	# mounted from the hosting VM
	if [[ -d config ]]; then
		cp config/*.conf .
	fi

	# wait for nexus up before installing, since installation
	# needs to deploy some artifacts to the repo
	./wait-for-port.sh nexus 8081

	./docker-install.sh

	. /opt/app/policy/etc/profile.d/env.sh

	# install policy keystore
	mkdir -p $POLICY_HOME/etc/ssl
	cp config/policy-keystore $POLICY_HOME/etc/ssl

	if [[ -x config/drools-tweaks.sh ]] ; then
		echo "Executing tweaks"
		# file may not be executable; running it as an
		# argument to bash avoids needing execute perms.
		bash config/drools-tweaks.sh
	fi

	# wait for DB up
	./wait-for-port.sh mariadb 3306

	# now that DB is up, invoke database upgrade:
	# sql provisioning scripts should be invoked here.
fi

echo "Starting processes"

policy start

sleep 1000d
