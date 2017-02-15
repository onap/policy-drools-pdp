#!/bin/bash

###
# ============LICENSE_START=======================================================
# Base Package
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

function usage() {
	echo -n "syntax: $(basename $0) "
	echo "[--debug]"
}

function log() {
	echo "$(date +"%Y-%m-%d_%H-%M-%S") $1" >> ${POLICY_HOME}/logs/monitor.log
}

function monitor() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} --"
		set -x
	fi
		
	CONTROLLER=$1
	STATUS=$2

	if [[ -z ${CONTROLLER} ]]; then
		log "WARNING: invalid invocation: no component provided"
		return
	fi
	
	if [[ -z ${STATUS} ]]; then
		log "WARNING: invalid invocation: no on/off/uninstalled switch provided for ${CONTROLLER}"
		return
	fi

	if [[ "${STATUS}" == "off" ]]; then
		off ${CONTROLLER}
	else
		if [[ "${STATUS}" == "on" ]]; then
			on ${CONTROLLER}
		fi
	fi
}

function on() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} --"
		set -x
	fi
		
	CONTROLLER=$1
	NAGIOS_COMPONENT_SERVICE="Check_${CONTROLLER}-AliveStatus_AP_24094"

	${POLICY_HOME}/bin/${CONTROLLER} status
	if [[ $? != 0 ]]; then
		log "starting ${CONTROLLER}"

		# need to make sure we don't pass the lock file descriptor
		${POLICY_HOME}/bin/${CONTROLLER} umstart {cfg}>&-
	else		
		log "OK: ${CONTROLLER} (UP)"
	fi
}

function off() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} --"
		set -x
	fi
		
	CONTROLLER=$1
	NAGIOS_COMPONENT_SERVICE="Check_${CONTROLLER}-AliveStatus_AP_24094"

	${POLICY_HOME}/bin/${CONTROLLER} status
	if [[ $? != 0 ]]; then
		log "OK: ${CONTROLLER} (DOWN)"

	else
		log "stopping ${CONTROLLER}"
		${POLICY_HOME}/bin/${CONTROLLER} umstop
	fi
}

function process_config() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} --"
		set -x
	fi
	
	CONF_FILE=${POLICY_HOME}/etc/monitor/monitor.cfg
	while read line || [ -n "${line}" ]; do
        if [[ -n ${line} ]] && [[ ${line} != *#* ]]; then
	        controller=$(echo "${line}" | awk -F = '{print $1;}')
	        status=$(echo "${line}" | awk -F = '{print $2;}')
			if [[ -n ${controller} ]] && [[ -n ${status} ]]; then
	    		monitor ${controller} ${status}
	    	fi
        fi
	done < "${CONF_FILE}"
	return 0
}

log "Enter monitor"

DEBUG=n
until [[ -z "$1" ]]; do
	case $1 in
		-d|--debug|debug)	DEBUG=y
							set -x
							;;								
		*)					usage
							exit 1
							;;
	esac
	shift
done

if pidof -o %PPID -x $(basename $0) > /dev/null 2>&1; then
	log "WARNING: $(basename $0) from the previous iteration still running.  Exiting."
	exit 1
fi

. ${POLICY_HOME}/etc/profile.d/env.sh

if [[ ${NAGIOS_NRDP_DISABLED} == true ]]; then
	log "Nagios NRDS is disabled."
fi

if flock ${cfg} ; then
	process_config
fi {cfg}>>${POLICY_HOME}/etc/monitor/monitor.cfg.lock


