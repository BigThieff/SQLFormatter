package com.bling.sqlformatter;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.SQLUtils.FormatOption;
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
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        if (project == null || psiFile == null || editor == null || !psiFile.getName().endsWith(".xml")) {
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
            WriteCommandAction.runWriteCommandAction(project, () -> document.setText(formattedText));
        }
    }

    private String formatXmlSqlTags(String xml) {
        Matcher matcher = SQL_TAG_PATTERN.matcher(xml);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String indent = matcher.group(1);
            String tag = matcher.group(2);
            String attrs = matcher.group(3);
            String sqlContent = matcher.group(4);

            String rawSql = extractSql(sqlContent);
            String formattedSql = SQLUtils.format(rawSql, DbType.mysql, new FormatOption(true, true));

            String indentedSql = formatWithIndent(formattedSql, indent + "    ");
            String replacement = indent + "<" + tag + attrs + ">\n"
                    + indent + "    <![CDATA[\n"
                    + indentedSql + "\n"
                    + indent + "    ]]>\n"
                    + indent + "</" + tag + ">";

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    private String extractSql(String sqlContent) {
        // 移除 CDATA 包裹（如果存在）
        if (sqlContent.contains("<![CDATA[")) {
            return sqlContent.replaceAll("<!\\[CDATA\\[", "")
                    .replaceAll("]]>", "")
                    .trim();
        }
        return sqlContent.trim();
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
        return str.length() > 500 ? str.substring(0, 500) + "\n...(too long)" : str;
    }

//    @Override
//    public void update(AnActionEvent e) {
//        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
//        e.getPresentation().setEnabledAndVisible(
//                psiFile != null && psiFile.getName().endsWith(".xml")
//        );
//    }

    @Override
    public void update(AnActionEvent e) {
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        boolean visible = psiFile != null;
        e.getPresentation().setEnabledAndVisible(visible);
    }
}
