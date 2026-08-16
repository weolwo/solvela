package sa.scriptengine.core;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.QLResult;
import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;
import com.alibaba.qlexpress4.security.QLSecurityStrategy;
import sa.scriptengine.domain.ExecutableScript;

import java.util.Collections;
import java.util.HashMap;

public class QLExpress4BugDetector {
    public static void main(String[] args) throws Exception {
        System.out.println("开始测试 QLExpress 4.1.0 原生绑定...");

        // 1. 创建原生引擎
        Express4Runner runner = new Express4Runner(InitOptions.DEFAULT_OPTIONS);

        // 2. 原生绑定自定义函数
        runner.addFunction("_substr", new CustomFunction() {
            @Override
            public Object call(QContext qContext, Parameters parameters) throws Throwable {

                return parameters.get(0).get(); // 模拟返回当前时间
            }
        });

        // 3. 执行脚本！
       // String script = "return _substring(\"hello\",2);";
        String script = "import sa.base.module.support.scriptengine.domain.ExecutableScript; executableScript = ExecutableScript.builder().content(\"jjjj\").name(\"kkk\").build();\n" +
                "        return executableScript.content;";
        System.out.println("👉 执行脚本: " + script);

        Express4Runner express4Runner = new Express4Runner(InitOptions.builder().securityStrategy(QLSecurityStrategy.open()).build());
        QLOptions qlOptions = QLOptions.builder().cache(true).build();
        QLResult result = express4Runner.execute(script, new HashMap<>(),qlOptions);

        System.out.println("✅ 执行成功！返回值: " + result);
    }
}