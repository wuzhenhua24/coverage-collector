package com.mofari.coveragecollector.util;

public class ReportUrlGenerator {

    private static final String BASE_URL = "https://jacoco.mofari.com";
    private static final String PATH_PREFIX_TO_REMOVE = "/data/coverage-reports";
    private static final String HTML_SUFFIX = "/html";

    /**
     * 根据给定的报告文件系统路径生成对应的可访问 URL。
     *
     * @param reportPath JaCoCo 报告在文件系统中的绝对路径，
     * 例如：/data/coverage-reports/appname/reg_20250513_01/report_20250612_172243
     * @return 对应的可访问 URL，例如：https://jacoco.mofari.com/appname/reg_20250513_01/report_20250612_172243/html
     * 如果 reportPath 为 null 或不符合预期格式，则返回 null。
     */
    public String generateReportUrl(String reportPath) {
        if (reportPath == null || reportPath.isEmpty()) {
            return null; // 或者抛出 IllegalArgumentException
        }

        // 检查路径是否以预期的前缀开始
        if (!reportPath.startsWith(PATH_PREFIX_TO_REMOVE)) {
            // 如果不以预期前缀开始，说明路径格式不符，无法生成正确URL
            System.err.println("Warning: Report path does not start with expected prefix: " + reportPath);
            return null; // 或者根据业务需求抛出异常
        }

        // 移除前缀
        String relativePath = reportPath.substring(PATH_PREFIX_TO_REMOVE.length());

        // 确保相对路径以 '/' 开头（如果原始路径没有，substring可能导致没有）
        if (!relativePath.startsWith("/")) {
            relativePath = "/" + relativePath;
        }

        // 拼接成最终URL

        return BASE_URL + relativePath + HTML_SUFFIX;
    }

    public static void main(String[] args) {
        ReportUrlGenerator generator = new ReportUrlGenerator();

        // 示例用法
        String reportPath1 = "/data/coverage-reports/haiercash-app/reg_20250513_01/report_20250612_172243";
        String reportUrl1 = generator.generateReportUrl(reportPath1);
        System.out.println("Report Path 1: " + reportPath1);
        System.out.println("Report URL 1 : " + reportUrl1); // Expected: https://jacoco.haierxj.cn/haiercash-app/reg_20250513_01/report_20250612_172243/html

        System.out.println("\n--- Test Cases ---");

        // 测试不完整或错误的路径
        String reportPath2 = "/data/wrong-path/app/report_123";
        String reportUrl2 = generator.generateReportUrl(reportPath2);
        System.out.println("Report Path 2: " + reportPath2);
        System.out.println("Report URL 2 : " + reportUrl2); // Expected: null (或根据你选择的错误处理方式)

        // 测试 null 或空路径
        String reportPath3 = null;
        String reportUrl3 = generator.generateReportUrl(reportPath3);
        System.out.println("Report Path 3: " + reportPath3);
        System.out.println("Report URL 3 : " + reportUrl3); // Expected: null

        String reportPath4 = "";
        String reportUrl4 = generator.generateReportUrl(reportPath4);
        System.out.println("Report Path 4: '" + reportPath4 + "'");
        System.out.println("Report URL 4 : " + reportUrl4); // Expected: null
    }
}
