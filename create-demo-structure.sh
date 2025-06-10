#!/bin/bash

echo "=== 创建演示项目目录结构 ==="

# 设置基础项目路径
BASE_PATH="$HOME/project"

echo "创建基础项目目录: $BASE_PATH"
mkdir -p "$BASE_PATH"

# 创建示例应用目录结构
apps=("user-service" "order-service" "payment-service")

for app in "${apps[@]}"; do
    echo "创建应用: $app"
    
    # 单模块结构
    mkdir -p "$BASE_PATH/$app/src/main/java"
    mkdir -p "$BASE_PATH/$app/target/classes"
    
    # 多模块结构
    mkdir -p "$BASE_PATH/$app/${app}-core/src/main/java"
    mkdir -p "$BASE_PATH/$app/${app}-core/target/classes"
    mkdir -p "$BASE_PATH/$app/${app}-api/src/main/java"
    mkdir -p "$BASE_PATH/$app/${app}-api/target/classes"
    
    # 创建示例Java文件
    cat > "$BASE_PATH/$app/src/main/java/Application.java" << 'EOF'
public class Application {
    public static void main(String[] args) {
        System.out.println("Hello World");
    }
}
EOF

    cat > "$BASE_PATH/$app/${app}-core/src/main/java/CoreService.java" << 'EOF'
public class CoreService {
    public void process() {
        System.out.println("Processing...");
    }
}
EOF

    cat > "$BASE_PATH/$app/${app}-api/src/main/java/ApiController.java" << 'EOF'
public class ApiController {
    public String hello() {
        return "Hello";
    }
}
EOF

    # 创建示例class文件（空文件用于演示）
    touch "$BASE_PATH/$app/target/classes/Application.class"
    touch "$BASE_PATH/$app/${app}-core/target/classes/CoreService.class"
    touch "$BASE_PATH/$app/${app}-api/target/classes/ApiController.class"
    
    echo "  - $app 目录结构创建完成"
done

echo ""
echo "目录结构创建完成！"
echo ""
echo "目录结构预览："
tree "$BASE_PATH" 2>/dev/null || find "$BASE_PATH" -type d | sort

echo ""
echo "你可以使用以下API测试路径发现功能："
echo "curl \"http://localhost:8080/api/coverage/app-config?appName=user-service\""
echo "curl \"http://localhost:8080/api/coverage/app-config?appName=order-service\""
echo "curl \"http://localhost:8080/api/coverage/app-config?appName=payment-service\"" 