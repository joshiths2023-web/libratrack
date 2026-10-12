package com.libratrack.model;

/** A catalogue entry with a number of physical copies. */
public class Book {
    private final long id;
    private final String title;
    private final String author;
    private final BookCategory category;
    private final int totalCopies;
    private final double price;
    private int availableCopies;

    public Book(long id, String title, String author, BookCategory category, int totalCopies, double price) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.category = category;
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies;
        this.price = price;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public BookCategory getCategory() {
        return category;
    }

    public int getTotalCopies() {
        return totalCopies;
    }

    public int getAvailableCopies() {
        return availableCopies;
    }

    public double getPrice() {
        return price;
    }

    public boolean isAvailable() {
        return availableCopies > 0;
    }

    /** Restores the stored availability when loading from the database. */
    public void restoreAvailableCopies(int available) {
        this.availableCopies = Math.max(0, Math.min(available, totalCopies));
    }

    public void checkOut() {
        if (availableCopies <= 0) {
            throw new IllegalStateException("No copies available for book " + id);
        }
        availableCopies--;
    }

    public void checkIn() {
        if (availableCopies >= totalCopies) {
            throw new IllegalStateException("All copies already in library for book " + id);
        }
        availableCopies++;
    }
}
