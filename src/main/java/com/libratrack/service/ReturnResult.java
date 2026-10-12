package com.libratrack.service;

import com.libratrack.model.Fine;
import com.libratrack.model.Transaction;

/** Outcome of returning one book. {@code fine} is null when nothing is owed. */
public record ReturnResult(Transaction transaction, Fine fine) {
}
