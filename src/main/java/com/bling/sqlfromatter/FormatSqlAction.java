package com.bling.sqlfromatter;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormatSqlAction extends AnAction {

    private static final Pattern SQL_TAG_PATTERN = Pattern.compile(
            "(\\s*)<(select|insert|update|delete)([^>]*)>([\\s\\S]*?)</\\2>",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;  // 选择在UI线程执行，如果动作逻辑是UI相关的
    }


    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        if (psiFile == null || editor == null || !psiFile.getName().endsWith(".xml")) {
            return;
        }

        Document document = editor.getDocument();
        String originalText = document.getText();

        String formattedText;
        try {
            formattedText = formatXmlSqlTags(originalText);
        } catch (Exception ex) {
            Messages.showErrorDialog("Fail to format: " + ex.getMessage(), "SQL Format Error");
            return;
        }

        int result = Messages.showYesNoDialog(
                project,
                "Original SQL:\n\n" + truncate(originalText) +
                        "\n\nAfter format:\n\n" + truncate(formattedText),
                "SQL Format Preview",
                "Confirm",
                "Cancel",
                Messages.getInformationIcon()
        );

        if (result == Messages.YES) {
            WriteCommandAction.runWriteCommandAction(project, () ->
                    document.setText(formattedText)
            );
        }
    }

    private String formatXmlSqlTags(String xml) {
        Matcher matcher = SQL_TAG_PATTERN.matcher(xml);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String indent = matcher.group(1);      // 缩进空格
            String tag = matcher.group(2);         // 标签名
            String attrs = matcher.group(3);       // 属性
            String sqlContent = matcher.group(4);  // SQL 原始内容

            // 格式化 SQL
            String formattedSql = SQLUtils.format(sqlContent, DbType.mysql);

            // 按缩进重新处理换行符（为每一行加上原始缩进 + 一个 TAB）
            String indentedSql = formatWithIndent(formattedSql, indent + "    ");

            // 构造替换文本
            String replacement = indent + "<" + tag + attrs + ">\n"
                    + indentedSql + "\n" + indent + "</" + tag + ">";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    private String formatWithIndent(String sql, String indent) {
        String[] lines = sql.trim().split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(indent).append(line.trim()).append("\n");
        }
        return sb.toString().trim();
    }

    private String truncate(String str) {
        return str.length() > 500 ? str.substring(0, 500) + "\n...(内容过长省略)" : str;
    }

    @Override
    public void update(AnActionEvent e) {
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabledAndVisible(
                psiFile != null && psiFile.getName().endsWith("Mapper.xml")
        );
    }
}