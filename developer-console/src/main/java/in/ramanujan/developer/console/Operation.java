package in.ramanujan.developer.console;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public interface Operation {
    public void execute(List<String> args) throws IOException;
}
