#!/bin/bash

# JaCoCo覆盖率配置部署脚本
# 功能：
# 1. 检查并拷贝jacocoagent.jar到目标机器
# 2. 检查端口冲突并自动分配可用端口
# 3. 在应用的vmoptions文件中添加JaCoCo参数

set -e  # 遇到错误立即退出

# =============================================================================
# 配置参数
# =============================================================================

# JaCoCo agent文件路径
LOCAL_JACOCO_AGENT="/path/to/local/jacocoagent.jar"  # 执行机上的jacocoagent.jar路径
REMOTE_JACOCO_AGENT="/app/appsystem/apps/jacocoagent.jar"  # 目标机器上的路径

# 应用相关路径
APPS_BASE_DIR="/app/appsystem/bin"  # 应用基础目录（vmoptions文件所在目录）
VMOPTIONS_SUFFIX=".vmoptions"  # vmoptions文件后缀

# JaCoCo配置
JACOCO_BASE_PORT=6300  # 起始端口
JACOCO_ADDRESS="0.0.0.0"  # 监听地址

# 目标服务器列表（可以通过参数传入或在这里配置）
TARGET_SERVERS=()

# 日志函数
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

log_error() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $1" >&2
}

# =============================================================================
# 函数定义
# =============================================================================

# 显示使用帮助
show_help() {
    cat << EOF
JaCoCo覆盖率配置部署脚本

用法: $0 [选项] <服务器1> <服务器2> ... 或 $0 [选项] -f <服务器列表文件>

选项:
    -h, --help              显示此帮助信息
    -f, --file FILE         从文件读取服务器列表
    -a, --app APPS          指定应用名称列表（逗号分隔，如：app1,app2,app3）
    -p, --port PORT         指定起始端口（默认：6300）
    -j, --jacoco FILE       指定本地jacocoagent.jar文件路径
    -u, --user USER         SSH用户名（默认：当前用户）
    -k, --key FILE          SSH私钥文件路径
    --dry-run               仅显示将要执行的操作，不实际执行
    --query                 查询JaCoCo端口（需要配合-a参数指定应用名）
    --list                  列出所有服务器上的JaCoCo配置

示例:
    $0 192.168.1.10 192.168.1.11 192.168.1.12
    $0 -a "app1,app2,app3" -u root 192.168.1.10
    $0 -f servers.txt -a "app1,app2"
    $0 --dry-run -a "app1" 192.168.1.10
    
查询示例:
    $0 --query -a "app1" 192.168.1.10              # 查询单个应用在单个服务器上的端口
    $0 --query -a "app1,app2" 192.168.1.10         # 查询多个应用在单个服务器上的端口
    $0 --query -a "app1" -f servers.txt            # 查询单个应用在多个服务器上的端口
    $0 --list 192.168.1.10                         # 列出单个服务器上所有JaCoCo配置
    $0 --list -f servers.txt                       # 列出多个服务器上所有JaCoCo配置

服务器列表文件格式（每行一个服务器）:
    192.168.1.10
    192.168.1.11
    user@192.168.1.12
EOF
}

# 检查本地jacocoagent.jar是否存在
check_local_jacoco() {
    if [[ ! -f "$LOCAL_JACOCO_AGENT" ]]; then
        log_error "本地JaCoCo agent文件不存在: $LOCAL_JACOCO_AGENT"
        log "请下载jacocoagent.jar并指定正确路径"
        exit 1
    fi
    log "本地JaCoCo agent文件检查通过: $LOCAL_JACOCO_AGENT"
}

# 检查SSH连接
check_ssh_connection() {
    local server=$1
    local ssh_user=$2
    local ssh_key_opt=$3
    
    log "检查与服务器 $server 的SSH连接..."
    if ssh $ssh_key_opt ${ssh_user:+$ssh_user@}$server "echo 'SSH连接正常'" >/dev/null 2>&1; then
        log "SSH连接检查通过: $server"
        return 0
    else
        log_error "无法连接到服务器: $server"
        return 1
    fi
}

# 在远程服务器上检查并拷贝jacocoagent.jar
setup_jacoco_agent() {
    local server=$1
    local ssh_user=$2
    local ssh_key_opt=$3
    
    log "检查服务器 $server 上的JaCoCo agent..."
    
    # 检查远程文件是否存在
    if ssh $ssh_key_opt ${ssh_user:+$ssh_user@}$server "test -f $REMOTE_JACOCO_AGENT" 2>/dev/null; then
        log "JaCoCo agent已存在于服务器 $server: $REMOTE_JACOCO_AGENT"
        return 0
    fi
    
    log "JaCoCo agent不存在，开始拷贝到服务器 $server..."
    
    # 创建目录
    ssh $ssh_key_opt ${ssh_user:+$ssh_user@}$server "mkdir -p $(dirname $REMOTE_JACOCO_AGENT)" || {
        log_error "无法在服务器 $server 上创建目录: $(dirname $REMOTE_JACOCO_AGENT)"
        return 1
    }
    
    # 拷贝文件
    scp $ssh_key_opt "$LOCAL_JACOCO_AGENT" ${ssh_user:+$ssh_user@}$server:$REMOTE_JACOCO_AGENT || {
        log_error "拷贝JaCoCo agent到服务器 $server 失败"
        return 1
    }
    
    log "JaCoCo agent拷贝成功到服务器 $server"
}

# 获取远程服务器上已使用的JaCoCo端口（从配置文件）
get_used_jacoco_ports() {
    local server=$1
    local ssh_user=$2
    local ssh_key_opt=$3
    
    # 在远程服务器上查找所有vmoptions文件中的JaCoCo端口
    ssh $ssh_key_opt ${ssh_user:+$ssh_user@}$server "
        find $APPS_BASE_DIR -name '*$VMOPTIONS_SUFFIX' -type f 2>/dev/null | xargs grep -h 'javaagent.*jacocoagent.jar.*port=' 2>/dev/null | \
        sed -n 's/.*port=\([0-9]\+\).*/\1/p' | sort -n | uniq
    " 2>/dev/null || echo ""
}

# 获取远程服务器上系统层面正在使用的端口
get_system_used_ports() {
    local server=$1
    local ssh_user=$2
    local ssh_key_opt=$3
    
    # 使用netstat检查正在监听的端口
    ssh $ssh_key_opt ${ssh_user:+$ssh_user@}$server "
        # 尝试使用netstat，如果不存在则使用ss
        if command -v netstat >/dev/null 2>&1; then
            netstat -tlnp 2>/dev/null | awk '\$1 == \"tcp\" && \$4 ~ /:/ {split(\$4, a, \":\"); print a[length(a)]}' | sort -n | uniq
        elif command -v ss >/dev/null 2>&1; then
            ss -tlnp 2>/dev/null | awk 'NR>1 && \$1 == \"LISTEN\" && \$4 ~ /:/ {split(\$4, a, \":\"); print a[length(a)]}' | sort -n | uniq
        else
            # 备用方案：检查/proc/net/tcp
            awk 'NR>1 {split(\$2, a, \":\"); print strtonum(\"0x\" a[2])}' /proc/net/tcp 2>/dev/null | sort -n | uniq
        fi
    " 2>/dev/null || echo ""
}

# 检查端口是否可用
is_port_available() {
    local server=$1
    local port=$2
    local ssh_user=$3
    local ssh_key_opt=$4
    
    # 检查端口是否被占用
    local port_in_use
    port_in_use=$(ssh $ssh_key_opt ${ssh_user:+$ssh_user@}$server "
        if command -v netstat >/dev/null 2>&1; then
            netstat -tlnp 2>/dev/null | grep -q \":$port \"
        elif command -v ss >/dev/null 2>&1; then
            ss -tlnp 2>/dev/null | grep -q \":$port \"
        else
            # 备用方案：尝试绑定端口
            timeout 2 bash -c \"</dev/tcp/localhost/$port\" 2>/dev/null
        fi
    " 2>/dev/null)
    
    # 如果命令执行成功（退出码为0），说明端口被占用
    if [[ $? -eq 0 ]]; then
        return 1  # 端口不可用
    else
        return 0  # 端口可用
    fi
}

# 获取下一个可用端口（同时检查配置文件和系统占用情况）
get_next_available_port() {
    local server=$1
    local used_ports_config=$2
    local used_ports_system=$3
    local start_port=$4
    local ssh_user=$5
    local ssh_key_opt=$6
    
    local port=$start_port
    local max_attempts=100  # 最大尝试次数，避免无限循环
    local attempts=0
    
    while [[ $attempts -lt $max_attempts ]]; do
        # 检查端口是否在配置文件中被使用
        if [[ "$used_ports_config" == *"$port"* ]]; then
            log "端口 $port 已在配置文件中使用，尝试下一个端口" >&2
            ((port++))
            ((attempts++))
            continue
        fi
        
        # 检查端口是否在系统层面被占用
        if [[ "$used_ports_system" == *"$port"* ]]; then
            log "端口 $port 已被系统占用，尝试下一个端口" >&2
            ((port++))
            ((attempts++))
            continue
        fi
        
        # 双重检查：实时检查端口是否可用
        if is_port_available "$server" "$port" "$ssh_user" "$ssh_key_opt"; then
            log "找到可用端口: $port" >&2
            echo $port
            return 0
        else
            log "端口 $port 实时检查显示被占用，尝试下一个端口" >&2
            ((port++))
            ((attempts++))
        fi
    done
    
    log_error "无法找到可用端口，已尝试 $max_attempts 次" >&2
    return 1
}

# 获取远程服务器上的应用列表
get_remote_apps() {
    local server=$1
    local ssh_user=$2
    local ssh_key_opt=$3
    
    ssh $ssh_key_opt ${ssh_user:+$ssh_user@}$server "
        find $APPS_BASE_DIR -name '*$VMOPTIONS_SUFFIX' -type f 2>/dev/null | \
        xargs -I {} basename {} $VMOPTIONS_SUFFIX 2>/dev/null | sort | uniq
    " 2>/dev/null || echo ""
}

# 检查应用的vmoptions文件中是否已有JaCoCo配置
has_jacoco_config() {
    local server=$1
    local app_name=$2
    local ssh_user=$3
    local ssh_key_opt=$4
    
    local vmoptions_file="$APPS_BASE_DIR/${app_name}$VMOPTIONS_SUFFIX"
    
    ssh $ssh_key_opt ${ssh_user:+$ssh_user@}$server "
        test -f '$vmoptions_file' && grep -q 'javaagent.*jacocoagent.jar' '$vmoptions_file'
    " 2>/dev/null
}

# 为应用添加JaCoCo配置
add_jacoco_config() {
    local server=$1
    local app_name=$2
    local port=$3
    local ssh_user=$4
    local ssh_key_opt=$5
    local dry_run=$6
    
    local vmoptions_file="$APPS_BASE_DIR/${app_name}$VMOPTIONS_SUFFIX"
    local jacoco_param="-javaagent:$REMOTE_JACOCO_AGENT=output=tcpserver,port=$port,address=$JACOCO_ADDRESS"
    
    if [[ "$dry_run" == "true" ]]; then
        log "[DRY-RUN] 将在 $server:$vmoptions_file 中添加: $jacoco_param"
        return 0
    fi
    
    # 检查vmoptions文件是否存在
    if ! ssh $ssh_key_opt ${ssh_user:+$ssh_user@}$server "test -f '$vmoptions_file'" 2>/dev/null; then
        log_error "vmoptions文件不存在: $server:$vmoptions_file"
        return 1
    fi
    
    # 添加JaCoCo参数
    ssh $ssh_key_opt ${ssh_user:+$ssh_user@}$server "
        # 备份原文件
        cp '$vmoptions_file' '${vmoptions_file}.backup.$(date +%Y%m%d_%H%M%S)'
        
        # 添加JaCoCo参数
        echo '$jacoco_param' >> '$vmoptions_file'
    " || {
        log_error "添加JaCoCo配置失败: $server:$vmoptions_file"
        return 1
    }
    
    log "成功为应用 $app_name 添加JaCoCo配置，端口: $port"
}

# 查询指定应用的JaCoCo端口
query_jacoco_port() {
    local server=$1
    local app_name=$2
    local ssh_user=$3
    local ssh_key_opt=$4
    
    local vmoptions_file="$APPS_BASE_DIR/${app_name}$VMOPTIONS_SUFFIX"
    
    # 检查文件是否存在
    if ! ssh $ssh_key_opt ${ssh_user:+$ssh_user@}$server "test -f '$vmoptions_file'" 2>/dev/null; then
        echo "N/A (文件不存在)"
        return 1
    fi
    
    # 提取端口号
    local port
    port=$(ssh $ssh_key_opt ${ssh_user:+$ssh_user@}$server "
        grep -o 'javaagent.*jacocoagent.jar.*port=[0-9]\+' '$vmoptions_file' 2>/dev/null | \
        sed -n 's/.*port=\([0-9]\+\).*/\1/p' | head -1
    " 2>/dev/null)
    
    if [[ -n "$port" ]]; then
        echo "$port"
        return 0
    else
        echo "N/A (未配置)"
        return 1
    fi
}

# 获取服务器上所有应用的JaCoCo配置
list_all_jacoco_configs() {
    local server=$1
    local ssh_user=$2
    local ssh_key_opt=$3
    
    log "查询服务器 $server 上的所有JaCoCo配置..."
    
    # 获取所有包含JaCoCo配置的vmoptions文件
    local configs
    configs=$(ssh $ssh_key_opt ${ssh_user:+$ssh_user@}$server "
        find '$APPS_BASE_DIR' -name '*$VMOPTIONS_SUFFIX' -type f 2>/dev/null | \
        xargs grep -l 'javaagent.*jacocoagent.jar' 2>/dev/null
    " 2>/dev/null)
    
    if [[ -z "$configs" ]]; then
        echo "服务器 $server: 未找到JaCoCo配置"
        return 0
    fi
    
    echo "服务器 $server JaCoCo配置:"
    echo "----------------------------------------"
    printf "%-20s %-10s %-15s %s\n" "应用名" "端口" "状态" "完整配置"
    echo "----------------------------------------"
    
    while IFS= read -r config_file; do
        [[ -z "$config_file" ]] && continue
        
        # 提取应用名
        local app_name
        app_name=$(basename "$config_file" "$VMOPTIONS_SUFFIX")
        
        # 提取JaCoCo配置
        local jacoco_line
        jacoco_line=$(ssh $ssh_key_opt ${ssh_user:+$ssh_user@}$server "
            grep 'javaagent.*jacocoagent.jar' '$config_file' 2>/dev/null | head -1
        " 2>/dev/null)
        
        if [[ -n "$jacoco_line" ]]; then
            # 提取端口
            local port
            port=$(echo "$jacoco_line" | sed -n 's/.*port=\([0-9]\+\).*/\1/p')
            
            # 检查端口状态
            local status="未知"
            if [[ -n "$port" ]]; then
                if is_port_available "$server" "$port" "$ssh_user" "$ssh_key_opt"; then
                    status="空闲"
                else
                    status="占用"
                fi
            fi
            
            printf "%-20s %-10s %-15s %s\n" "$app_name" "${port:-N/A}" "$status" "$jacoco_line"
        fi
    done <<< "$configs"
    
    echo "----------------------------------------"
}

# 查询模式主函数
query_mode() {
    local apps_list=$1
    local ssh_user=$2
    local ssh_key_opt=$3
    local list_all=$4
    
    if [[ "$list_all" == "true" ]]; then
        # 列出所有JaCoCo配置
        log "列出所有服务器上的JaCoCo配置..."
        for server in "${TARGET_SERVERS[@]}"; do
            if check_ssh_connection "$server" "$ssh_user" "$ssh_key_opt"; then
                list_all_jacoco_configs "$server" "$ssh_user" "$ssh_key_opt"
                echo
            fi
        done
    else
        # 查询特定应用的端口
        if [[ -z "$apps_list" ]]; then
            log_error "查询模式需要指定应用名（使用 -a 参数）"
            return 1
        fi
        
        log "查询应用端口配置..."
        echo "========================================"
        printf "%-15s %-20s %-10s %s\n" "服务器" "应用名" "端口" "状态说明"
        echo "========================================"
        
        for server in "${TARGET_SERVERS[@]}"; do
            if ! check_ssh_connection "$server" "$ssh_user" "$ssh_key_opt"; then
                continue
            fi
            
            for app in $apps_list; do
                app=$(echo "$app" | tr -d ' ')  # 去除空格
                [[ -z "$app" ]] && continue
                
                local port
                port=$(query_jacoco_port "$server" "$app" "$ssh_user" "$ssh_key_opt")
                local status=""
                
                if [[ "$port" == "N/A"* ]]; then
                    status="$port"
                elif [[ -n "$port" ]]; then
                    if is_port_available "$server" "$port" "$ssh_user" "$ssh_key_opt"; then
                        status="端口空闲"
                    else
                        status="端口占用"
                    fi
                fi
                
                printf "%-15s %-20s %-10s %s\n" "$server" "$app" "${port:-N/A}" "$status"
            done
        done
        echo "========================================"
    fi
}
process_server() {
    local server=$1
    local ssh_user=$2
    local ssh_key_opt=$3
    local apps_list=$4
    local dry_run=$5
    
    log "开始处理服务器: $server"
    
    # 检查SSH连接
    if ! check_ssh_connection "$server" "$ssh_user" "$ssh_key_opt"; then
        return 1
    fi
    
    # 设置JaCoCo agent
    if [[ "$dry_run" != "true" ]]; then
        if ! setup_jacoco_agent "$server" "$ssh_user" "$ssh_key_opt"; then
            return 1
        fi
    else
        log "[DRY-RUN] 将检查并拷贝JaCoCo agent到 $server"
    fi
    
    # 获取已使用的端口（配置文件中的）
    local used_ports_config
    used_ports_config=$(get_used_jacoco_ports "$server" "$ssh_user" "$ssh_key_opt")
    log "服务器 $server 配置文件中已使用的JaCoCo端口: ${used_ports_config:-无}"
    
    # 获取系统层面正在使用的端口
    local used_ports_system
    used_ports_system=$(get_system_used_ports "$server" "$ssh_user" "$ssh_key_opt")
    log "服务器 $server 系统层面正在使用的端口: ${used_ports_system:-无}"
    
    # 确定要处理的应用列表
    local target_apps
    if [[ -n "$apps_list" ]]; then
        target_apps="$apps_list"
    else
        target_apps=$(get_remote_apps "$server" "$ssh_user" "$ssh_key_opt")
        log "服务器 $server 发现的应用: ${target_apps:-无}"
    fi
    
    if [[ -z "$target_apps" ]]; then
        log "服务器 $server 上没有找到应用或应用列表为空"
        return 0
    fi
    
    # 为每个应用分配端口并添加配置
    local current_port=$JACOCO_BASE_PORT
    for app in $target_apps; do
        app=$(echo "$app" | tr -d ' ')  # 去除空格
        [[ -z "$app" ]] && continue
        
        # 检查是否已有JaCoCo配置
        if has_jacoco_config "$server" "$app" "$ssh_user" "$ssh_key_opt"; then
            log "应用 $app 已有JaCoCo配置，跳过"
            continue
        fi
        
        # 获取可用端口
        local available_port
        available_port=$(get_next_available_port "$server" "$used_ports_config" "$used_ports_system" "$current_port" "$ssh_user" "$ssh_key_opt")
        
        if [[ -z "$available_port" ]]; then
            log_error "无法为应用 $app 找到可用端口"
            continue
        fi
        
        # 添加JaCoCo配置
        if add_jacoco_config "$server" "$app" "$available_port" "$ssh_user" "$ssh_key_opt" "$dry_run"; then
            used_ports_config="$used_ports_config $available_port"  # 更新已使用端口列表
            current_port=$((available_port + 1))  # 为下一个应用准备端口
        fi
    done
    
    log "服务器 $server 处理完成"
}

# =============================================================================
# 主程序
# =============================================================================

main() {
    local apps_list=""
    local servers_file=""
    local ssh_user=""
    local ssh_key=""
    local ssh_key_opt=""
    local dry_run="false"
    local query_mode="false"
    local list_mode="false"
    
    # 解析命令行参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -f|--file)
                servers_file="$2"
                shift 2
                ;;
            -a|--app)
                apps_list=$(echo "$2" | tr ',' ' ')
                shift 2
                ;;
            -p|--port)
                JACOCO_BASE_PORT="$2"
                shift 2
                ;;
            -j|--jacoco)
                LOCAL_JACOCO_AGENT="$2"
                shift 2
                ;;
            -u|--user)
                ssh_user="$2"
                shift 2
                ;;
            -k|--key)
                ssh_key="$2"
                ssh_key_opt="-i $ssh_key"
                shift 2
                ;;
            --dry-run)
                dry_run="true"
                shift
                ;;
            --query)
                query_mode="true"
                shift
                ;;
            --list)
                list_mode="true"
                shift
                ;;
            -*)
                log_error "未知选项: $1"
                show_help
                exit 1
                ;;
            *)
                TARGET_SERVERS+=("$1")
                shift
                ;;
        esac
    done
    
    # 从文件读取服务器列表
    if [[ -n "$servers_file" ]]; then
        if [[ ! -f "$servers_file" ]]; then
            log_error "服务器列表文件不存在: $servers_file"
            exit 1
        fi
        while IFS= read -r line; do
            [[ -n "$line" && ! "$line" =~ ^[[:space:]]*# ]] && TARGET_SERVERS+=("$line")
        done < "$servers_file"
    fi
    
    # 检查是否为查询模式
    if [[ "$query_mode" == "true" || "$list_mode" == "true" ]]; then
        query_mode "$apps_list" "$ssh_user" "$ssh_key_opt" "$list_mode"
        exit $?
    fi
    
    # 检查是否有目标服务器
    if [[ ${#TARGET_SERVERS[@]} -eq 0 ]]; then
        log_error "没有指定目标服务器"
        show_help
        exit 1
    fi
    
    # 检查本地JaCoCo agent文件
    if [[ "$dry_run" != "true" ]]; then
        check_local_jacoco
    fi
    
    log "开始部署JaCoCo配置..."
    log "目标服务器: ${TARGET_SERVERS[*]}"
    log "应用列表: ${apps_list:-自动检测}"
    log "起始端口: $JACOCO_BASE_PORT"
    [[ "$dry_run" == "true" ]] && log "运行模式: DRY-RUN（仅预览，不执行实际操作）"
    
    # 处理每个服务器
    local success_count=0
    local total_count=${#TARGET_SERVERS[@]}
    
    for server in "${TARGET_SERVERS[@]}"; do
        if process_server "$server" "$ssh_user" "$ssh_key_opt" "$apps_list" "$dry_run"; then
            ((success_count++))
        fi
        echo "----------------------------------------"
    done
    
    # 输出汇总结果
    log "部署完成！成功: $success_count/$total_count"
    
    if [[ $success_count -eq $total_count ]]; then
        log "所有服务器部署成功"
        exit 0
    else
        log_error "部分服务器部署失败"
        exit 1
    fi
}

# 执行主程序
main "$@"