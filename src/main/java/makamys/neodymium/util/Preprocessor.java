package makamys.neodymium.util;

import lombok.val;

import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class Preprocessor {
    
    public static String preprocess(String text, Map<String, String> defines) {
        String[] lines = text.replaceAll("\\r\\n", "\n").split("\\n");

        val ifElseBlockStatus = new Stack<IfElseBlockStatus>();
        ifElseBlockStatus.push(IfElseBlockStatus.NONE);
        val ifElseConditionMet = new Stack<Boolean>();
        
        for(int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            boolean commentLine = false;

            String preProcLine = line.trim();
            if(preProcLine.startsWith("#ifdef ")) {
                ifElseBlockStatus.push(IfElseBlockStatus.IF);
                boolean prev = ifElseConditionMet.isEmpty() || ifElseConditionMet.peek();
                ifElseConditionMet.push(prev && defines.containsKey(preProcLine.split(" ")[1]));
                commentLine = true;
            } else if(preProcLine.startsWith("#else")) {
                if (ifElseBlockStatus.peek() == IfElseBlockStatus.NONE) {
                    throw new IllegalStateException("#else encountered outside of an ifdef block!");
                }
                val curr = !ifElseConditionMet.pop();
                val prev = ifElseConditionMet.isEmpty() || ifElseConditionMet.peek();
                ifElseConditionMet.push(prev && curr);
                ifElseBlockStatus.pop();
                ifElseBlockStatus.push(IfElseBlockStatus.ELSE);
                commentLine = true;
            } else if(preProcLine.startsWith("#endif")) {
                if (ifElseBlockStatus.peek() == IfElseBlockStatus.NONE) {
                    throw new IllegalStateException("#endif encountered outside of an ifdef/else block!");
                }
                ifElseBlockStatus.pop();
                ifElseConditionMet.pop();
                commentLine = true;
            } else {
                if(ifElseBlockStatus.peek() == IfElseBlockStatus.IF && !ifElseConditionMet.peek()) {
                    commentLine = true;
                }
                if(ifElseBlockStatus.peek() == IfElseBlockStatus.ELSE && !ifElseConditionMet.peek()) {
                    commentLine = true;
                }
            }
            
            if(commentLine) {
                lines[i] = "//" + line;
            } else {
                for (Map.Entry<String, String> define: defines.entrySet()) {
                    line = line.replace(define.getKey(), define.getValue());
                }
                lines[i] = line;
            }
        }
        
        return String.join("\n", lines);
    }
    
    public static enum IfElseBlockStatus {
        NONE, IF, ELSE;
    }
    
}
