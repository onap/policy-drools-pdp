#!/bin/bash

###
# ============LICENSE_START=======================================================
# Installation Package
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


function JAVA_HOME() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} $@ --"
		set -x
	fi
		
	if [[ -z ${JAVA_HOME} ]]; then
		echo "error: aborting installation: JAVA_HOME variable must be present in base.conf"
		exit 1;
	fi
	
	echo "JAVA_HOME is ${JAVA_HOME}"
}

function POLICY_HOME() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} $@ --"
		set -x
	fi
	
	local POLICY_HOME_ABS
	
	if [[ -z ${POLICY_HOME} ]]; then
		echo "error: aborting installation: the installation directory POLICY_HOME must be set"
		exit 1
	fi
	
	POLICY_HOME_ABS=$(readlink -f "${POLICY_HOME}")
	if [[ -n ${POLICY_HOME_ABS} ]]; then
		export POLICY_HOME=${POLICY_HOME_ABS}
	fi
	
	echo "POLICY_HOME is ${POLICY_HOME}"
	
	# Do not allow installations from within POLICY_HOME dir or sub-dirs
	if [[ "$(pwd)/" == ${POLICY_HOME}/* ]]; then
	 	echo "error: aborting installation: cannot be executed from '${POLICY_HOME}' or sub-directories. "
	 	exit 1
	fi
}

function check_java() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} $@ --"
		set -x
	fi
	
	local TARGET_JAVA_VERSION INSTALLED_JAVA_VERSION
	
	TARGET_JAVA_VERSION=$1
	
	if [[ -z ${JAVA_HOME} ]]; then
		echo "error: ${JAVA_HOME} is not set"
		return 1
	fi
	
	if ! check_x_file "${JAVA_HOME}/bin/java"; then
		echo "error: ${JAVA_HOME}/bin/java is not accessible"
		return 1
	fi
	
	INSTALLED_JAVA_VERSION=$("${JAVA_HOME}/bin/java" -version 2>&1 | awk -F '"' '/version/ {print $2}')
	if [[ -z $INSTALLED_JAVA_VERSION ]]; then
		echo "error: ${JAVA_HOME}/bin/java is invalid"
		return 1
	fi
	
	if [[ "${INSTALLED_JAVA_VERSION}" != ${TARGET_JAVA_VERSION}* ]]; then
		echo "error: java version (${INSTALLED_JAVA_VERSION}) does not"\
			 "march desired version ${TARGET_JAVA_VERSION}"
		return 1
	fi 
	
	echo "OK: java ${INSTALLED_JAVA_VERSION} installed"
	
	if ! type -p "${JAVA_HOME}/bin/keytool" > /dev/null 2>&1; then
		echo "error: {JAVA_HOME}/bin/keytool is not installed"
		return 1
	fi
}

function process_configuration() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} $@ --"
		set -x
	fi

	local CONF_FILE name value
	
	CONF_FILE=$1
	while read line || [ -n "${line}" ]; do
        if [[ -n ${line} ]] && [[ ${line} != *#* ]]; then
	        name=$(echo "${line%%=*}")
	        value=$(echo "${line#*=}")
	        # escape ampersand so that sed does not replace it with the search string
            value=${value//&/\\&}
	        if [[ -z ${name} ]] || [[ -z $value ]]; then
	        	echo "WARNING: ${line} missing name or value"
	    	fi
	    	export ${name}="${value}"
	        eval "${name}" "${value}" 2> /dev/null
        fi
	done < "${CONF_FILE}"
	return 0
}

function component_preinstall() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} $@ --"
		set -x
	fi

	/bin/sed -i -e 's!${{POLICY_HOME}}!'"${POLICY_HOME}!g" \
		-e 's!${{FQDN}}!'"${FQDN}!g" \
		*.conf > /dev/null 2>&1
}

function configure_component() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} $@ --"
		set -x
	fi

	local CONF_FILE COMPONENT_ROOT_DIR SED_LINE SED_FILES name value
		
	CONF_FILE=$1
	COMPONENT_ROOT_DIR=$2
	
	SED_LINE="sed -i"
	SED_LINE+=" -e 's!\${{POLICY_HOME}}!${POLICY_HOME}!g' "
	SED_LINE+=" -e 's!\${{POLICY_USER}}!${POLICY_USER}!g' "
	SED_LINE+=" -e 's!\${{POLICY_GROUP}}!${POLICY_GROUP}!g' "
	SED_LINE+=" -e 's!\${{KEYSTORE_PASSWD}}!${KEYSTORE_PASSWD}!g' "
	SED_LINE+=" -e 's!\${{JAVA_HOME}}!${JAVA_HOME}!g' "
		
	while read line || [ -n "${line}" ]; do
		if [[ -n ${line} ]] && [[ ${line:0:1} != \# ]]; then
			name=$(echo "${line%%=*}")
			value=$(echo "${line#*=}")
			# escape ampersand so that sed does not replace it with the search string
			value=$(echo "${value}" | sed -e 's/[\/&]/\\&/g')
	    	if [[ -z ${name} ]] || [[ -z ${value} ]]; then
	        	echo "WARNING: ${line} missing name or value"
	    	fi
	    	SED_LINE+=" -e 's/\${{${name}}}/${value}/g' "
        fi
	done < "$CONF_FILE"
	
	SED_FILES=""
	for sed_file in $(find "${COMPONENT_ROOT_DIR}" -type f -exec grep -Iq . {} \; -print 2> /dev/null); do
		if fgrep -l '${{' ${sed_file} > /dev/null 2>&1; then
			SED_FILES+="${sed_file} "
		fi
	done

	if [[ -z ${SED_FILES} ]]; then
		echo "WARNING: no files to perform variable expansion"
	else
		SED_LINE+=${SED_FILES}
		eval "${SED_LINE}"
	fi
}

function configure_settings() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} $@ --"
		set -x
	fi

	# The goal is to have repositories for both 'release' and 'snapshot'
	# artifacts. These may either be remote (e.g. Nexus) repositories, or
	# a local file-based repository. 
	local fileRepoID=file-repository
	local fileRepoUrl=file:$HOME_M2/file-repository
	mkdir -p "${fileRepoUrl#file:}"
		
	# The following parameters are also used outside of this function.
	# if snapshotRepositoryUrl and/or releaseRepositoryUrl is defined,
	# the corresponding ID and url will be updated below
	releaseRepoID=${fileRepoID}
	releaseRepoUrl=${fileRepoUrl}
	snapshotRepoID=${fileRepoID}
	snapshotRepoUrl=${fileRepoUrl}

	# if both snapshotRepositoryUrl and releaseRepositoryUrl are null,
	# use standalone-settings.xml that just defines the file-based repo.
	# if only one of them is specified, use file-based repo for the other.
	if [[ -z "$snapshotRepositoryUrl" && -z $releaseRepositoryUrl ]]; then
		echo "snapshotRepositoryUrl and releaseRepositoryUrl properties not set, configuring settings.xml for standalone operation"
		mv $HOME_M2/standalone-settings.xml $HOME_M2/settings.xml
	else
		rm $HOME_M2/standalone-settings.xml

		if [[ -n "${snapshotRepositoryUrl}" ]] ; then
			snapshotRepoID=${snapshotRepositoryID}
			snapshotRepoUrl=${snapshotRepositoryUrl}
		fi
		if [[ -n "${releaseRepositoryUrl}" ]] ; then
			releaseRepoID=${releaseRepositoryID}
			releaseRepoUrl=${releaseRepositoryUrl}
		fi
	fi

	SED_LINE="sed -i"
	SED_LINE+=" -e 's!\${{snapshotRepositoryID}}!${snapshotRepoID}!g' "
	SED_LINE+=" -e 's!\${{snapshotRepositoryUrl}}!${snapshotRepoUrl}!g' "
	SED_LINE+=" -e 's!\${{releaseRepositoryID}}!${releaseRepoID}!g' "
	SED_LINE+=" -e 's!\${{releaseRepositoryUrl}}!${releaseRepoUrl}!g' "
	SED_LINE+=" -e 's!\${{repositoryUsername}}!${repositoryUsername}!g' "
	SED_LINE+=" -e 's!\${{repositoryPassword}}!${repositoryPassword}!g' "
	SED_LINE+=" -e 's!\${{fileRepoID}}!${fileRepoID}!g' "
	SED_LINE+=" -e 's!\${{fileRepoUrl}}!${fileRepoUrl}!g' "
	
	SED_LINE+="$HOME_M2/settings.xml"
	eval "${SED_LINE}"
	
}


function check_r_file() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} $@ --"
		set -x
	fi

	FILE=$1
	if [[ ! -f ${FILE} || ! -r ${FILE} ]]; then
        return 1
	fi

	return 0
}

function check_x_file() {	
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} $@ --"
		set -x
	fi

	FILE=$1
	if [[ ! -f ${FILE} || ! -x ${FILE} ]]; then
        return 1
	fi

	return 0
}

function install_prereqs() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} $@ --"
		set -x
	fi

	local CONF_FILE HOME_OWNER
	
	CONF_FILE=$1
	
	if ! check_r_file "${CONF_FILE}"; then
		echo "error: aborting ${COMPONENT_TYPE} installation: ${CONF_FILE} is not accessible"
		exit 1
	fi
	
	if ! process_configuration "${CONF_FILE}"; then
		echo "error: aborting ${COMPONENT_TYPE} installation: cannot process configuration ${CONF_FILE}"
		exit 1
	fi
	
	if ! check_java "1.8"; then
		echo "error: aborting ${COMPONENT_TYPE} installation: invalid java version"
		exit 1
	fi
	

	if [[ -z ${POLICY_HOME} ]]; then
		echo "error: aborting ${COMPONENT_TYPE} installation: ${POLICY_HOME} is not set"
		exit 1	
	fi

	HOME_OWNER=$(ls -ld "${POLICY_HOME}" | awk '{print $3}')
	if [[ ${HOME_OWNER} != ${POLICY_USER} ]]; then
		echo "error: aborting ${COMPONENT_TYPE} installation: ${POLICY_USER} does not own ${POLICY_HOME} directory"
		exit 1
	fi
	
	echo -n "Starting ${OPERATION} of ${COMPONENT_TYPE} under ${POLICY_USER}:${POLICY_GROUP} "
	echo "ownership with umask $(umask)."
}

function configure_base() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} $@ --"
		set -x
	fi

	local BASH_PROFILE_LINE PROFILE_LINE
	
	# check if fqdn is set in base.conf and use that value if set
	if [[ -z ${INSTALL_FQDN} ]]
	then
		echo "FQDN not set in config...using the default FQDN ${FQDN}"
	else
		echo "Using FQDN ${INSTALL_FQDN} from config"
		FQDN=${INSTALL_FQDN}
	fi

	configure_component "${BASE_CONF}" "${POLICY_HOME}"
	
	configure_settings
	
	BASH_PROFILE_LINE=". ${POLICY_HOME}/etc/profile.d/env.sh"
	PROFILE_LINE="ps -p \$\$ | grep -q bash || . ${POLICY_HOME}/etc/profile.d/env.sh"

	# Note: adding to .bashrc instead of .bash_profile
	if ! fgrep -x "${BASH_PROFILE_LINE}" "${HOME}/.bashrc" >/dev/null 2>&1; then
		echo "${BASH_PROFILE_LINE}" >> "${HOME}/.bashrc"
	fi

	if ! fgrep -x "${PROFILE_LINE}" "${HOME}/.profile" >/dev/null 2>&1; then
		echo "${PROFILE_LINE}" >> "${HOME}/.profile"
	fi

	. "${POLICY_HOME}/etc/profile.d/env.sh"
	
	cat "${POLICY_HOME}"/etc/cron.d/* | crontab
}

function install_base() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} $@ --"
		set -x
	fi

	local POLICY_HOME_CONTENTS BASE_TGZ BASEX_TGZ BASH_PROFILE_LINE
	
	install_prereqs "${BASE_CONF}"

	# following properties must be set:
	# POLICY_HOME - installation directory, must exist and be writable

	# test that all required properties are set
	for var in POLICY_HOME JAVA_HOME
	do
		if [[ -z $(eval echo \$$var) ]]; then
			echo "ERROR: $var must be set in $BASE_CONF"
			exit 1
		fi
	done

	if [[ ! ( -d "$POLICY_HOME" && -w "$POLICY_HOME" ) ]]; then
		echo "ERROR: Installation directory $POLICY_HOME does not exist or not writable"
		exit 1
	fi

	if ! /bin/rm -fr "${POLICY_HOME}"/* > /dev/null 2>&1; then
		echo "error: aborting base installation: cannot delete the underlying ${POLICY_HOME} files"
		exit 1
	fi
	
	POLICY_HOME_CONTENTS=$(ls -A "${POLICY_HOME}" 2> /dev/null)
	if [[ -n ${POLICY_HOME_CONTENTS} ]]; then
		echo "error: aborting base installation: ${POLICY_HOME} directory is not empty"
		exit 1
	fi
	
	if ! /bin/mkdir -p "${POLICY_HOME}/logs/" > /dev/null 2>&1; then	
		echo "error: aborting base installation: cannot create ${POLICY_HOME}/logs/"
		exit 1
	fi	
	
	BASE_TGZ=$(ls base-*.tar.gz)
	if [ ! -r ${BASE_TGZ} ]; then
		echo "error: aborting: base package is not accessible"
		exit 1			
	fi
	
	tar -tzf ${BASE_TGZ} > /dev/null 2>&1
	if [[ $? != 0 ]]; then
		echo >&2 "error: aborting installation: invalid base package file: ${BASE_TGZ}"
		exit 1
	fi
	
	BASEX_TGZ=$(ls basex-*.tar.gz 2> /dev/null)
	if [ -z ${BASEX_TGZ} ]; then
		echo "warning: no basex application package present"
		BASEX_TGZ=
	else
		tar -tzf ${BASEX_TGZ} > /dev/null 2>&1
		if [[ $? != 0 ]]; then
			echo >&2 "warning: invalid basex application package tar file: ${BASEX_TGZ}"
			BASEX_TGZ=
		fi			
	fi
	
	# Undo any changes in the $HOME directory if any
	
	BASH_PROFILE_LINE=". ${POLICY_HOME}/etc/profile.d/env.sh"
#	PROFILE_LINE="ps -p \$\$ | grep -q bash || . ${POLICY_HOME}/etc/profile.d/env.sh"
		
	# Note: using .bashrc instead of .bash_profile
	if [[ -f ${HOME}/.bashrc ]]; then
		/bin/sed -i.bak "\:${BASH_PROFILE_LINE}:d" "${HOME}/.bashrc"
	fi
	
#	if [[ -f ${HOME}/.profile ]]; then
#		/bin/sed -i.bak "\:${PROFILE_LINE}:d" "${HOME}/.profile"
#	fi
	
	tar -C ${POLICY_HOME} -xf ${BASE_TGZ} --no-same-owner
	if [[ $? != 0 ]]; then
		# this should not happened
		echo "error: aborting base installation: base package cannot be unpacked: ${BASE_TGZ}"
		exit 1
	fi
	
	if [ ! -z ${BASEX_TGZ} ]; then
		tar -C ${POLICY_HOME} -xf ${BASEX_TGZ} --no-same-owner
		if [[ $? != 0 ]]; then
			# this should not happened
			echo "warning: basex package cannot be unpacked: ${BASEX_TGZ}"
		fi
	fi

#	/bin/mkdir -p ${POLICY_HOME}/etc/ssl > /dev/null 2>&1
#	/bin/mkdir -p ${POLICY_HOME}/etc/init.d > /dev/null 2>&1
#	/bin/mkdir -p ${POLICY_HOME}/nagios/tmp > /dev/null 2>&1
#	/bin/mkdir -p ${POLICY_HOME}/tmp > /dev/null 2>&1
#	/bin/mkdir -p ${POLICY_HOME}/var > /dev/null 2>&1
			
#	chmod -R 755 ${POLICY_HOME}/nagios > /dev/null 2>&1
	
	if [[ -d $HOME_M2 ]]; then
		echo "Renaming existing $HOME_M2 to $HOME/m2.$TIMESTAMP"
		mv $HOME_M2 $HOME/m2.$TIMESTAMP
		if [[ $? != 0 ]]; then
			echo "WARNING: Failed to rename $HOME_M2 directory; will use old directory"
		fi
	fi
	if [[ ! -d $HOME_M2 ]]; then
		echo "Moving m2 directory to $HOME_M2"
		mv $POLICY_HOME/m2 $HOME_M2
		if [[ $? != 0 ]]; then
			echo "ERROR: Error in moving m2 directory"
			exit 1
		fi
	fi
	
	configure_base
	
	# save ${BASE_CONF} in PDP-D installation
	cp "${BASE_CONF}" "${POLICY_HOME}"/etc/profile.d
	
#	if ! create_keystore; then
#		echo "error: aborting base installation: creating keystore"
#		exit 1
#	fi
	
#	list_unexpanded_files ${POLICY_HOME}

}

function install_controller()
{
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} $@ --"
		set -x
	fi

	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} $@ --"
		set -x
	fi
	
	if [[ -f "${HOME}/.bashrc" ]]; then
		source "${HOME}/.bashrc"
	fi
	
	if [[ -z ${POLICY_HOME} ]]; then
		echo "error: aborting installation: POLICY_HOME environment variable is not set."
		exit 1	
	fi
	
	if ! check_r_file ${POLICY_HOME}/etc/profile.d/env.sh; then
		echo "error: aborting installation: ${POLICY_HOME}/etc/profile.d/env.sh is not accessible"
		exit 1	
	fi

	local CONTROLLER_CONF CONTROLLER_ZIP RULES_JAR SOURCE_DIR CONTROLLER_DIR AAAA BBBB PORT UTOPIC ARTIFACT_VERSION
	
	CONTROLLER_CONF=$COMPONENT_TYPE.conf
	install_prereqs "${CONTROLLER_CONF}"

	# following properties must be set in conf file:
	# CONTROLLER_ARTIFACT_ID - Maven artifactId for controller
	# CONTROLLER_NAME - directory name for the controller; controller will be installed to
	#                   $POLICY_HOME/controllers/$CONTROLLER_NAME
	# CONTROLLER_PORT - port number for the controller REST interface
	# RULES_ARTIFACT -  rules artifact specifier: groupId:artifactId:version
	
	# test that all required properties are set
	for var in CONTROLLER_ARTIFACT_ID CONTROLLER_NAME CONTROLLER_PORT RULES_ARTIFACT UEB_TOPIC
	do
		if [[ -z $(eval echo \$$var) ]]; then
			echo "ERROR: $var must be set in $CONTROLLER_CONF"
			exit 1
		fi
	done
	
	CONTROLLER_ZIP=$(ls $CONTROLLER_ARTIFACT_ID*.zip 2>&-)
	if [[ -z $CONTROLLER_ZIP ]]; then
		echo "ERROR: Cannot find controller zip file ($CONTROLLER_ARTIFACT_ID*.zip)"
		exit 1
	fi

	if [[ ! "$CONTROLLER_NAME" =~ ^[A-Za-z0-9_-]+$ ]]; then
		echo "ERROR: CONTROLLER_NAME may only contain alphanumeric, underscore, and dash characters"
		exit 1
	fi

	if [[ ! "$CONTROLLER_PORT" =~ ^[0-9]+$ ]]; then
		echo "ERROR: CONTROLLER_PORT is not a valid integer"
		exit 1
	fi

	# split artifact string into parts
	IFS=: read RULES_GROUPID RULES_ARTIFACTID RULES_VERSION <<<$RULES_ARTIFACT
	if [[ -z $RULES_GROUPID || -z $RULES_ARTIFACTID || -z $RULES_VERSION ]]; then
		echo "ERROR: Invalid setting for RULES_ARTIFACT property"
		exit 1
	fi

	#RULES_JAR=$RULES_ARTIFACTID-$RULES_VERSION.jar
	RULES_JAR=$(echo ${RULES_ARTIFACTID}-*.jar)
	if ! check_r_file $RULES_JAR; then
		echo "WARNING: Rules jar file $RULES_JAR not found in installer package, must be installed manually"
		RULES_JAR=
	fi


	SOURCE_DIR=$PWD
	CONTROLLER_DIR=$POLICY_HOME

	cd $CONTROLLER_DIR

	echo "Unpacking controller zip file"
	# use jar command in case unzip not present on system
	jar xf $SOURCE_DIR/$CONTROLLER_ZIP
	if [[ $? != 0 ]]; then
		echo "ERROR: unpack of controller zip file failed, install aborted"
		exit 1
	fi

	chmod +x bin/*

	# Perform base variable replacement in controller config file
	configure_component "${SOURCE_DIR}/${BASE_CONF}" "${CONTROLLER_DIR}"
	
	# Perform variable replacements in config files.
	# config files may contain the following strings that need to be replaced with
	# real values:
	#	AAAA - artifactId
	#	BBBB - Substring of AAAA after first dash (stripping initial "ncomp-" or "policy-")
	#	PORT - Port number for REST server

	echo "Performing variable replacement in config files"
	AAAA=$CONTROLLER_ARTIFACT_ID
	BBBB=${AAAA#[a-z]*-}
	PORT=$CONTROLLER_PORT
	UTOPIC=${UEB_TOPIC}

	for file in config/*
	do
		sed -i -e "s/AAAA/$AAAA/" -e "s/BBBB/$BBBB/" -e "s/PORT/$PORT/" -e "s!\${{UEB_TOPIC}}!${UTOPIC}!" $file
		if [[ $? != 0 ]]; then
			echo "ERROR: variable replacement failed for file $file, install aborted"
			exit 1
		fi
	done

	# append properties for rules artifact to server properties
	cat >>config/server.properties <<EOF

rules.groupId=$RULES_GROUPID
rules.artifactId=$RULES_ARTIFACTID
rules.version=$RULES_VERSION
EOF

	# TODO: run pw.sh script to set passwords

	# return to directory where we started
	cd $SOURCE_DIR
	
	# install rules jar into repository if present
	if [[ -n $RULES_JAR ]]; then
		# can't use RULES_VERSION because may be set to "LATEST",
		# so extract version from the jar filename
		ARTIFACT_VERSION=$(sed -e "s/${RULES_ARTIFACTID}-//" -e "s/\.jar//" <<<${RULES_JAR})
		if [[ -n $repositoryUrl ]]; then
			echo "Deploying rules artifact to Policy Repository"
			mvn deploy:deploy-file -Dfile=$RULES_JAR \
				-DgroupId=$RULES_GROUPID -DartifactId=$RULES_ARTIFACTID -Dversion=$ARTIFACT_VERSION \
				-DrepositoryId=${repositoryID} -Durl=${repositoryUrl} \
				-DgeneratePom=true -DupdateReleaseInfo=true
		else
			echo "Installing rules artifact into local .m2 repository"
			mvn --offline org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file \
				-Dfile=$RULES_JAR -DgeneratePom=true -DupdateReleaseInfo=true
		fi
	fi

	update_monitor $CONTROLLER_NAME

	# save install configuration as an environment file
	ln -s -f "${POLICY_HOME}/etc/profile.d/${BASE_CONF}" "${POLICY_HOME}/config/${BASE_CONF}.environment"
}


function update_monitor() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} $@ --"
		set -x
	fi

	local NAME lastline
	
	NAME=$1
	
	if [[ -f ${POLICY_HOME}/etc/monitor/monitor.cfg ]]; then
		if grep -q "^${NAME}=" ${POLICY_HOME}/etc/monitor/monitor.cfg; then
			echo "OK: updating monitoring entry for ${NAME}"
			/bin/sed -i.bak \
				-e "s/^${NAME}=.*/${NAME}=off/g" \
				${POLICY_HOME}/etc/monitor/monitor.cfg
		else
			# make sure file ends with newline
			lastline=$(tail -n 1 ${POLICY_HOME}/etc/monitor/monitor.cfg; echo x)
			lastline=${lastline%x}
			if [ "${lastline: -1}" = $'\n' ]; then
				echo "OK: adding an entry for ${NAME} in ${POLICY_HOME}/etc/monitor/monitor.cfg"
			else
				echo "OK: adding an entry for ${NAME} in ${POLICY_HOME}/etc/monitor/monitor.cfg (with newline)"
				echo "" >> ${POLICY_HOME}/etc/monitor/monitor.cfg
			fi


			echo "${NAME}=off" >> ${POLICY_HOME}/etc/monitor/monitor.cfg
		fi
	else
		echo "WARNING: ${POLICY_HOME}/etc/monitor/monitor.cfg does not exist. No monitoring enabled."	
	fi
}

# Usage: getPomAttributes <pom-file> <attribute> ...
#
# This function performs simplistic parsing of a 'pom.xml' file, extracting
# the specified attributes (e.g. 'groupId', 'artifactId', 'version'). The
# attributes are returned as environment variables with the associated name.

function getPomAttributes
{
	local tab=$'\t'
	local rval=0
	local file="$1"
	local attr
	local value
	shift
	for attr in "$@" ; do
		# Try to fetch the parameter associated with the 'pom.xml' file.
		# Initially, the 'parent' element is excluded. If the desired
		# parameter is not found, the 'parent' element is included in the
		# second attempt.
		value=$(sed -n \
			-e '/<parent>/,/<\/parent>/d' \
			-e '/<dependencies>/,/<\/dependencies>/d' \
			-e '/<build>/,/<\/build>/d' \
			-e "/^[ ${tab}]*<${attr}>\([^<]*\)<\/${attr}>.*/{s//\1/p;}" \
			<"${file}")

		if [[ "${value}" == "" ]] ; then
			# need to check parent for parameter
			value=$(sed -n \
				-e '/<dependencies>/,/<\/dependencies>/d' \
				-e '/<build>/,/<\/build>/d' \
				-e "/^[ ${tab}]*<${attr}>\([^<]*\)<\/${attr}>.*/{s//\1/p;}" \
				<"${file}")
			if [[ "${value}" == "" ]] ; then
				echo "${file}: Can't determine ${attr}" >&2
				rval=1
			fi
		fi
		# the following sets an environment variable with the name referred
		# to by ${attr}
		read ${attr} <<<"${value}"
	done
	return ${rval}
}


# Usage: installPom <pom-file>
#
# This function installs a 'pom.xml' file in the local repository

function installPom
{
	# need to extract attributes from POM file
	if getPomAttributes "${1}" artifactId groupId version ; then
		local repoID repoUrl
		if [[ "${version}" =~ SNAPSHOT ]] ; then
			repoID=${snapshotRepoID}
			repoUrl=${snapshotRepoUrl}
		else
			repoID=${releaseRepoID}
			repoUrl=${releaseRepoUrl}
		fi
		echo "${1}: Deploying POM artifact to remote repository"
		mvn deploy:deploy-file -Dfile="$1" \
			-Dpackaging=pom -DgeneratePom=false \
			-DgroupId=${groupId} \
			-DartifactId=${artifactId} \
			-Dversion=${version} \
			-DrepositoryId=${repoID} -Durl=${repoUrl} \
			-DupdateReleaseInfo=true
	else
		echo "${1}: Can't install pom due to missing attributes" >&2
		return 1
	fi
}

# Usage: installJar <jar-file>
#
# This function installs a JAR file in the local repository, as well as
# the 'pom.xml' member it contains.

function installJar
{
	local dir=$(mktemp -d)
	local jar="${1##*/}"
	cp -p "${1}" "${dir}/${jar}"

	(
		local rval=0
		cd "${dir}"
		# determine name of 'pom' file within JAR
		local pom=$(jar tf ${jar} META-INF | grep '/pom\.xml$' | head -1)
		if [[ "${pom}" ]] ; then
			# extract pom file
			jar xf ${jar} "${pom}"

			# determine version from pom file
			if getPomAttributes "${pom}" version ; then
				local repoID repoUrl
				if [[ "${version}" =~ SNAPSHOT ]] ; then
					repoID=${snapshotRepoID}
					repoUrl=${snapshotRepoUrl}
				else
					repoID=${releaseRepoID}
					repoUrl=${releaseRepoUrl}
				fi
				echo "${1}: Deploying JAR artifact to remote repository"
				mvn deploy:deploy-file \
					-Dfile=${jar} \
					-Dversion=${version} \
					-Dpackaging=jar -DgeneratePom=false -DpomFile=${pom} \
					-DrepositoryId=${repoID} -Durl=${repoUrl} \
					-DupdateReleaseInfo=true
			else
				echo "${1}: Can't determine version from 'pom.xml'" >&2
				rval=1
			fi
		else
			echo "${1}: Can't find 'pom.xml'" >&2
			rval=1
		fi
		rm -rf ${dir}
		return ${rval}
	)
}

# Unzip the 'artifacts-*.zip' file, and install all of the associated
# artifacts into the local repository.

function installArtifacts
{
	local file
	if [[ -f $(echo artifacts-*.zip) ]] ; then
		# use jar command in case unzip not present on system
		jar xf artifacts-*.zip
		for file in artifacts/* ; do
			case "${file}" in
				*pom.xml|*.pom) installPom "${file}";;
				*.jar) installJar "${file}";;
				*) echo "${file}: Don't know how to install artifact" >&2;;
			esac
		done
	fi
}

function installFeatures
{
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} $@ --"
		set -x
	fi

	local name featureConf
	export FEATURES_HOME="${POLICY_HOME}/${FEATURES_DIR}"
	echo "FEATURES_HOME is ${FEATURES_HOME}"
	
	mkdir -p "${FEATURES_HOME}" > /dev/null 2>&1
	if [[ -d "${FEATURES_HOME}" && -x "${FEATURES_HOME}" ]]; then
		SOURCE_DIR=$PWD
		for feature in feature-*.zip ; do
			name="${feature#feature-}"
			name="${name%-[0-9]*\.zip}"
			mkdir -p "${FEATURES_HOME}/${name}" > /dev/null 2>&1
			(cd "${FEATURES_HOME}/${name}"; jar xf ${SOURCE_DIR}/${feature})
			featureConf="feature-${name}.conf"
			if [[ -r "${featureConf}" ]]; then
				configure_component "${featureConf}" "${FEATURES_HOME}"
				cp "${featureConf}" "${POLICY_HOME}"/etc/profile.d
				echo "feature ${name} has been installed (configuration present)"
			else
				echo "feature ${name} has been installed (no configuration present)"
			fi
		done
		
		echo "applying base configuration to features"
		configure_component "${BASE_CONF}" "${FEATURES_HOME}"
	else
		echo "error: aborting ${FEATURES_HOME} is not accessible"
		exit 1
	fi
}

function do_install()
{
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} $@ --"
		set -x
	fi

	echo "Starting installation at $(date)"
	echo
	
	COMPONENT_TYPE=base
	BASE_CONF=base.conf
	install_base
	component_preinstall

	COMPONENT_TYPE=policy-management
	install_controller
	
	installFeatures
	installArtifacts


	if [[ -f apps-installer ]]; then
		# if exists, any customizations to the 
		# base drools installation from the drools apps
		# is executed here

		./apps-installer
	fi
	
	echo
	echo "Installation complete"
	echo "Please logoff and login again to update shell environment"
	
}

export POLICY_USER=$(/usr/bin/id -un)
export POLICY_GROUP=$POLICY_USER
	
FQDN=$(hostname -f 2> /dev/null)
if [[ $? != 0 || -z ${FQDN} ]]; then
	echo "error: cannot determine the FQDN for this host $(hostname)."
	exit 1
fi

TIMESTAMP=$(date "+%Y%m%d-%H%M%S")
LOGFILE=$PWD/install.log.$TIMESTAMP

OPERATION=install
BASE_CONF=base.conf
HOME_M2=$HOME/.m2
FEATURES_DIR="features"

do_install 2>&1 | tee $LOGFILE
