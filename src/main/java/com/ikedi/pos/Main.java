package com.ikedi.pos;

/**
 * Root package entry point that forwards to {@link com.ikedi.pos.cli.Main}.
 */
public class Main {

    public static void main(String[] args) {
        com.ikedi.pos.cli.Main.main(args);
    }
}
