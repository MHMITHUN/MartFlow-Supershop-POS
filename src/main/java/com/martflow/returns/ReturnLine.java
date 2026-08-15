package com.martflow.returns;

import java.math.BigDecimal;

/** One merchandise line of a return: how many of the sold units come back, and why. */
public record ReturnLine(int saleLineNo, String name, BigDecimal quantity, String reason) {
}
