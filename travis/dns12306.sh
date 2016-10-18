#!/bin/bash
##########################################################
# 提取所有12306公网ip 并按相应时间排序
#
# created by zhanghuajie02  2016.10.17
#
##########################################################

declare DOMAIN_NAME="kyfw.12306.cn";
declare ALL_IP_FILENAME="/opt/all_ip.log";
declare DNS_IP_TIME="/opt/dns_ip_time.log";
declare PUBLIC_IP_FILE="/opt/public_ip.log";

#OneDNS  （112.124.47.27）
dns_server[0]="112.124.47.27"
#OpenerDNS（42.120.21.30）
dns_server[1]="42.120.21.30"
#BaiduDNS （180.76.76.76）
dns_server[2]="180.76.76.76"
#aliDNS （223.5.5.5， 223.6.6.6）
dns_server[3]="223.5.5.5"
#114DNS （114.114.114.114， 114.114.115.115）
dns_server[4]="114.114.114.114"
#114DNS安全版 （114.114.114.119， 114.114.115.119）
dns_server[5]="114.114.114.119"
#114DNS家庭版 （114.114.114.110， 114.114.115.110）
dns_server[6]="114.114.114.110"
#Dns派：电信/移动/铁通 （101.226.4.6， 218.30.118.6）
dns_server[7]="101.226.4.6"
#Dns派：联通 （123.125.81.6， 140.207.198.6）
dns_server[8]="123.125.81.6"

regex_ip="(2[0-4][0-9]|25[0-5]|1[0-9][0-9]|[1-9]?[0-9])(\.(2[0-4][0-9]|25[0-5]|1[0-9][0-9]|[1-9]?[0-9])){3}"


> $ALL_IP_FILENAME
for dns in ${dns_server[@]}
do
    dns_ips=$(dig +short @$dns $DOMAIN_NAME | grep -E "$regex_ip")
    OLD_IFS="$IFS"
    IFS=" "
    array=($dns_ips)
    IFS="$OLD_IFS"
    for each in ${array[*]}
    do
        echo $each>>$ALL_IP_FILENAME
    done
done

ips=$(sort $ALL_IP_FILENAME | uniq)


> $DNS_IP_TIME
for ip in $ips
do
    avg=$(ping -c 5 $ip|grep avg|awk -F/ '{print $5}')
    echo $ip,$avg>>$DNS_IP_TIME
done
sort -n -k 2 -t , $DNS_IP_TIME>$PUBLIC_IP_FILE

