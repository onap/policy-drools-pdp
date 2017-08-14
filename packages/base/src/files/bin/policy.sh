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
	echo -n "[--debug] "
	echo    "status|start|stop"
}

function check_x_file() {
        if [[ $DEBUG == y ]]; then
                echo "-- ${FUNCNAME[0]} --"
                set -x
        fi

        FILE=$1
        if [[ ! -f ${FILE} || ! -x ${FILE} ]]; then
        return 1
        fi

        return 0
}

function policy_op() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} --"
		set -x
	fi

	operation=$1
	
	cd $POLICY_HOME
	echo "[drools-pdp-controllers]"
	binScript="bin/policy-management-controller"
	if check_x_file "${binScript}"; then
		trap "rm -f /tmp/out$$" EXIT
		${binScript} ${operation} >/tmp/out$$
		echo " L [${controller}]: $(sed ':a;N;$!ba;s/\n/ /g' /tmp/out$$)"
	else
		echo "	L [${controller}]: -"
	fi
}

function policy_status() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} --"
		set -x
	fi
	
	echo
	policy_op "status"

	NUM_CRONS=$(crontab -l 2> /dev/null | wc -l)
	echo "	${NUM_CRONS} cron jobs installed."

	echo
	echo "[features]"
	features status
	
	local databases=$(ls -d "${POLICY_HOME}"/etc/db/migration/*/ 2> /dev/null)
	if [[ -n ${databases} ]]; then
		echo "[migration]"
		db-migrator -s ALL -o ok
	fi
	
}

function policy_start() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} --"
		set -x
	fi
	
	policy_op "start"
}


function policy_stop() {
	if [[ $DEBUG == y ]]; then
		echo "-- ${FUNCNAME[0]} --"
		set -x
	fi
	
	policy_op "stop"
}

#########################################################################
##
## script execution body
##
#########################################################################

DEBUG=n
OPERATION=none

until [[ -z "$1" ]]; do
	case $1 in
		-d|--debug|debug) 	DEBUG=y
						set -x
						;;
		-i|--status|status) 		OPERATION=status
						;;
		-s|--start|start)		OPERATION=start
						;;
		-h|--stop|stop|--halt|halt)	OPERATION=halt
						;;
		*)				usage
						exit 1
						;;
	esac
	shift
done

# operation validation
case $OPERATION in
	status)	;;
	start)	;;
	halt)	;;
	*)	echo "invalid operation \(${OPERATION}\): must be in {status|start|stop}";
		usage
		exit 1
		;;
esac

if [[ -z ${POLICY_HOME} ]]; then
	echo "error: POLICY_HOME is unset."
	exit 1
fi

# operation validation
case $OPERATION in
	status)	
		policy_status
		;;
	start)	
		policy_start
		;;
	halt)	
		policy_stop
		;;
	*)		echo "invalid operation \(${OPERATION}\): must be in {status|start|stop}";
			usage
			exit 1
			;;
esac
