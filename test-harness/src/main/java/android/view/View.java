package android.view;

public class View {
    private int width;
    private int height;

    public View() {
        this(1920, 1080);
    }

    public View(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }
}
