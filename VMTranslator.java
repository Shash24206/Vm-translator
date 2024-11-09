package vm;

import java.io.*;
import java.util.*;

public class VMTranslator {
    private static final Map<String, String> ARITH_DICT = Map.of(
        "add", "+", "sub", "-", "neg", "-", "eq", "JEQ",
        "gt", "JGT", "lt", "JLT", "and", "&", "or", "|",
        "not", "!"
    );
    private static final Map<String, String> SEGMENT_MAPPING = Map.of(
        "local", "LCL", "argument", "ARG", "this", "THIS",
        "that", "THAT", "temp", "5", "pointer", "3"
    );

    private List<String> asmCode;
    private int labelCounter;

    public VMTranslator(String inputFilePath) {
        asmCode = new ArrayList<>();
        labelCounter = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().split("//")[0].trim();
                if (!line.isEmpty()) {
                    translateLine(line.split("\\s+"));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void translateLine(String[] parts) {
        switch (parts[0]) {
            case "push" -> push(parts[1], parts[2]);
            case "pop" -> pop(parts[1], parts[2]);
            case "label" -> label(parts[1]);
            case "goto" -> goTo(parts[1]);
            case "if-goto" -> ifGoTo(parts[1]);
            case "function" -> function(parts[1], Integer.parseInt(parts[2]));
            case "call" -> call(parts[1], Integer.parseInt(parts[2]));
            case "return" -> returnCommand();
            default -> arithmetic(parts[0]);
        }
    }

    private void push(String segment, String index) {
        if ("constant".equals(segment)) {
            asmCode.add("@" + index);
            asmCode.add("D=A");
        } else {
            String baseAddress = SEGMENT_MAPPING.get(segment);
            asmCode.add("@" + index);
            asmCode.add("D=A");
            asmCode.add("@" + baseAddress);
            if (segment.equals("temp") || segment.equals("pointer")) {
                asmCode.add("A=A+D");
            } else {
                asmCode.add("A=M+D");
            }
            asmCode.add("D=M");
        }
        asmCode.addAll(Arrays.asList(
            "@SP", "A=M", "M=D", "@SP", "M=M+1"
        ));
    }

    private void pop(String segment, String index) {
        String baseAddress = SEGMENT_MAPPING.get(segment);
        asmCode.add("@" + index);
        asmCode.add("D=A");
        asmCode.add("@" + baseAddress);
        if (segment.equals("temp") || segment.equals("pointer")) {
            asmCode.add("D=A+D");
        } else {
            asmCode.add("D=M+D");
        }
        asmCode.addAll(Arrays.asList(
            "@R13", "M=D",
            "@SP", "AM=M-1", "D=M",
            "@R13", "A=M", "M=D"
        ));
    }

    private void arithmetic(String command) {
        if (ARITH_DICT.containsKey(command)) {
            if (command.equals("neg") || command.equals("not")) {
                asmCode.addAll(Arrays.asList(
                    "@SP", "A=M-1", "M=" + ARITH_DICT.get(command) + "M"
                ));
            } else if (command.equals("eq") || command.equals("gt") || command.equals("lt")) {
                String jumpCommand = ARITH_DICT.get(command);
                String labelTrue = "LABEL_TRUE_" + labelCounter;
                String labelEnd = "LABEL_END_" + labelCounter;
                labelCounter++;
                asmCode.addAll(Arrays.asList(
                    "@SP", "AM=M-1", "D=M", "A=A-1",
                    "D=M-D", "@" + labelTrue, "D;" + jumpCommand,
                    "@SP", "A=M-1", "M=0",
                    "@" + labelEnd, "0;JMP",
                    "(" + labelTrue + ")", "@SP", "A=M-1", "M=-1",
                    "(" + labelEnd + ")"
                ));
            } else {
                asmCode.addAll(Arrays.asList(
                    "@SP", "AM=M-1", "D=M", "A=A-1", "M=M" + ARITH_DICT.get(command) + "D"
                ));
            }
        }
    }

    private void label(String label) {
        asmCode.add("(" + label + ")");
    }

    private void goTo(String label) {
        asmCode.addAll(Arrays.asList(
            "@" + label, "0;JMP"
        ));
    }

    private void ifGoTo(String label) {
        asmCode.addAll(Arrays.asList(
            "@SP", "AM=M-1", "D=M", "@" + label, "D;JNE"
        ));
    }

    private void function(String functionName, int numLocals) {
        asmCode.add("(" + functionName + ")");
        for (int i = 0; i < numLocals; i++) {
            asmCode.addAll(Arrays.asList(
                "@SP", "A=M", "M=0", "@SP", "M=M+1"
            ));
        }
    }

    private void call(String functionName, int numArgs) {
        String returnLabel = "RETURN_LABEL_" + labelCounter++;
        asmCode.addAll(Arrays.asList(
            "@" + returnLabel, "D=A", "@SP", "A=M", "M=D", "@SP", "M=M+1",
            "@LCL", "D=M", "@SP", "A=M", "M=D", "@SP", "M=M+1",
            "@ARG", "D=M", "@SP", "A=M", "M=D", "@SP", "M=M+1",
            "@THIS", "D=M", "@SP", "A=M", "M=D", "@SP", "M=M+1",
            "@THAT", "D=M", "@SP", "A=M", "M=D", "@SP", "M=M+1",
            "@SP", "D=M", "@5", "D=D-A", "@" + numArgs, "D=D-A", "@ARG", "M=D",
            "@SP", "D=M", "@LCL", "M=D",
            "@" + functionName, "0;JMP",
            "(" + returnLabel + ")"
        ));
    }

    private void returnCommand() {
        asmCode.addAll(Arrays.asList(
            "@LCL", "D=M", "@R13", "M=D",
            "@5", "A=D-A", "D=M", "@R14", "M=D",
            "@SP", "AM=M-1", "D=M", "@ARG", "A=M", "M=D",
            "@ARG", "D=M+1", "@SP", "M=D",
            "@R13", "AM=M-1", "D=M", "@THAT", "M=D",
            "@R13", "AM=M-1", "D=M", "@THIS", "M=D",
            "@R13", "AM=M-1", "D=M", "@ARG", "M=D",
            "@R13", "AM=M-1", "D=M", "@LCL", "M=D",
            "@R14", "A=M", "0;JMP"
        ));
    }

    public void saveToFile(String outputFilePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {
            for (String line : asmCode) {
                writer.write(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String inputFilePath = "vm/input.vm";  
        String outputFilePath = "vm/output.asm";
        VMTranslator translator = new VMTranslator(inputFilePath);
        translator.saveToFile(outputFilePath);
    }
}
