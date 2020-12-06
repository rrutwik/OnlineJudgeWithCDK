package com.myorg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class MyProjectTest {

  private final static ObjectMapper JSON =
      new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);

  @Test
  public void testStack() throws IOException {
  }
}
