/*
 *    Copyright (c) 2018 - 2020, Thales DIS CPL Canada, Inc
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package com.thales.chaos.constants;

import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.IpRange;

public abstract class AwsEC2Constants {
    public static final int AWS_PENDING_CODE = 0;
    public static final int AWS_RUNNING_CODE = 16;
    public static final int AWS_SHUTTING_DOWN_CODE = 32;
    public static final int AWS_STOPPING_CODE = 64;
    public static final int AWS_TERMINATED_CODE = 48;
    public static final int AWS_STOPPED_CODE = 80;
    public static final String EC2_DEFAULT_CHAOS_SECURITY_GROUP_NAME = "ChaosEngine Security Group";
    public static final String EC2_DEFAULT_CHAOS_SECURITY_GROUP_DESCRIPTION = "(DO NOT USE) Security Group used by Chaos Engine to simulate random network failures.";
    public static final int AWS_EC2_HARD_REBOOT_TIMER_MINUTES = 4;
    public static final String NO_GROUPING_IDENTIFIER = "No Grouping Identifier Found";
    public static final String AWS_ASG_NAME_TAG_KEY = "aws:autoscaling:groupName";
    public static final String NO_ASSIGNED_KEY = "No Assigned Key";
    public static final IpPermission DEFAULT_IP_PERMISSIONS = new IpPermission().withIpProtocol("-1").withIpv4Ranges(new IpRange().withCidrIp("0.0.0.0/0"));
    public static final String SECURITY_GROUP_NOT_FOUND = "InvalidGroup.NotFound";
    public static final String DEFAULT_EC2_CLI_USER = "ec2-user";

    private AwsEC2Constants () {
    }

    public static int[] getAwsUnhealthyCodes () {
        return new int[]{ AWS_PENDING_CODE, AWS_SHUTTING_DOWN_CODE, AWS_STOPPING_CODE, AWS_STOPPED_CODE, AWS_STOPPED_CODE };
    }
}
