package com.example.calculator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalculatorTest {

    @Test void add() { assertEquals(5.0, Calculator.add(2, 3)); }

    @Test void subtract() { assertEquals(-1.0, Calculator.subtract(2, 3)); }

    @Test void multiply() { assertEquals(6.0, Calculator.multiply(2, 3)); }

    @Test void divide() { assertEquals(2.0, Calculator.divide(10, 5)); }

    @Test
    void divideByZero_throwsArithmeticException() {
        assertThrows(ArithmeticException.class, () -> Calculator.divide(5, 0));
    }
}
