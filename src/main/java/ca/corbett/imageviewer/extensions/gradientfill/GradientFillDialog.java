package ca.corbett.imageviewer.extensions.gradientfill;

import ca.corbett.extras.MessageUtil;
import ca.corbett.extras.gradient.ColorSelectionType;
import ca.corbett.extras.image.ImagePanel;
import ca.corbett.extras.image.ImagePanelConfig;
import ca.corbett.extras.image.ImageUtil;
import ca.corbett.extras.io.KeyStrokeManager;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.fields.ColorField;
import ca.corbett.forms.fields.LabelField;
import ca.corbett.forms.fields.NumberField;
import ca.corbett.forms.fields.PanelField;
import ca.corbett.imageviewer.extensions.ImageViewerExtensionManager;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides options for applying a gradient to a given image, by selecting not only the gradient
 * colors, but also by interactively selecting two points on the image to define the gradient's direction and length.
 * The gradient can therefore be applied at any angle by carefully positioning the two point markers.
 * <p>
 * A live preview is shown here in this dialog. The user has the option of reverting back to the original
 * image without closing the dialog, to allow "starting over" with new options. The user also has
 * "save and close" and "cancel" options. If the source image was a PNG, the gradient's alpha channel (if any)
 * will be preserved. Otherwise, if saving to JPEG, the gradient will be saved as solid colors, even if the
 * gradient itself was partially transparent.
 * </p>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class GradientFillDialog extends JDialog {
    private MessageUtil messageUtil;
    private final KeyStrokeManager keyStrokeManager;
    private final File srcFile;
    private BufferedImage originalImage;
    private BufferedImage dBuffer;
    private int imgWidth;
    private int imgHeight;
    private final ImagePanel imagePanel;
    private BufferedImage gradientPreviewBuffer;

    private ColorField startColorField;
    private ColorField endColorField;
    private NumberField startXField;
    private NumberField startYField;
    private NumberField endXField;
    private NumberField endYField;
    private boolean suppressGradientRender;
    private DragTarget activeDragTarget = DragTarget.NONE;

    private enum DragTarget {
        NONE,
        START,
        END
    }

    /**
     * Creates a new GradientFillDialog based on the image represented by the given file.
     * We rely on our launching action to ensure that we only ever receive a JPEG or PNG file.
     *
     * @param file The File containing the image to be edited.
     */
    public GradientFillDialog(File file) {
        super(MainWindow.getInstance(), "Gradient fill", true);
        this.srcFile = file;
        this.keyStrokeManager = new KeyStrokeManager(this);
        addWindowListener(new WindowCleanupListener());
        setMinimumSize(new Dimension(500, 500));
        // Start maximized (or effectively maximized... can't use setExtendedState( MAXIMIZED_BOTH ) on a dialog)
        setSize(new Dimension(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().width,
                              GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height));
        setResizable(true);
        setLocationRelativeTo(MainWindow.getInstance());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        imagePanel = new ImagePanel(ImagePanelConfig.createSimpleReadOnlyProperties());
        add(buildControlPanel(), BorderLayout.WEST);
        add(imagePanel, BorderLayout.CENTER);
        PointDragListener pointDragListener = new PointDragListener();
        imagePanel.addMouseListener(pointDragListener);
        imagePanel.addMouseMotionListener(pointDragListener);
        configureKeyboardShortcuts();
        loadImage();
    }

    /**
     * Invoked internally to reset back to the original supplied image, so we can start over.
     */
    private void resetAll() {
        startColorField.setColor(Color.BLACK); // Solid black
        endColorField.setColor(new Color(0,0,0,0)); // Fully transparent
        startXField.setCurrentValue(0);
        startYField.setCurrentValue(0);
        endXField.setCurrentValue(originalImage.getWidth() - 1);
        endYField.setCurrentValue(originalImage.getHeight() - 1);
        updateGradient();
    }

    /**
     * Prompts the user for confirmation, and if confirmed,
     * saves the current edit (if needed) and then closes the dialog.
     * If the user does not confirm, the dialog does NOT close.
     */
    private void promptToSaveAndExit() {
        if (getMessageUtil().askYesNo("Confirm",
                                      "Save current changes and close?\nThis will overwrite the original image.")
            == MessageUtil.YES) {
            saveChanges(); // will dispose() on success
        }
    }

    /**
     * Saves the current edit and then closes the dialog.
     * Changes are saved in-place to the source file, so as soon as the dialog closes, the main image panel
     * in the MainWindow should already be updated to show the result.
     */
    private void saveChanges() {
        // if we're in a wonky state, I guess we're done here:
        if (dBuffer == null || originalImage == null) {
            getMessageUtil().info("Nothing to save.");
            dispose();
            return;
        }

        // Start with the original image:
        Graphics2D graphics = dBuffer.createGraphics();
        Graphics2D targetGraphics = originalImage.createGraphics();
        try {
            graphics.drawImage(originalImage, 0, 0, null);
            drawGradient(graphics);
            graphics.dispose();
            graphics = null;
            targetGraphics.drawImage(dBuffer, 0, 0, null);
            targetGraphics.dispose();
            targetGraphics = null;

            getMessageUtil().getLogger().log(Level.INFO, "Gradient fill: saving image {0}", srcFile.getAbsolutePath());
            if (srcFile.getName().toLowerCase(Locale.ROOT).endsWith("png")) {
                ImageUtil.savePngImage(originalImage, srcFile);
            }
            else if (srcFile.getName().toLowerCase(Locale.ROOT).endsWith("jpg") ||
            srcFile.getName().toLowerCase(Locale.ROOT).endsWith("jpeg")) {
                ImageUtil.saveImage(originalImage, srcFile);
            }
            else{
                throw new IOException("Unsupported image format; must be png or jpeg image.");
            }

            // Force thumbnail regeneration for this image:
            ImageViewerExtensionManager.getInstance().removeThumbnail(srcFile);

            // Force reload of current image in MainWindow:
            MainWindow.getInstance().reloadCurrentImage();

            // We're done here:
            dispose();
        }
        catch (IOException ioe) {
            getMessageUtil().error("Problem saving image: " + ioe.getMessage(), ioe);
        }
        finally {
            if (graphics != null) {
                graphics.dispose();
            }
            if (targetGraphics != null) {
                targetGraphics.dispose();
            }
        }
    }

    /**
     * Invoked internally to render the gradient with current settings onto the original image
     * and show the results in our preview area. Also shows our two draggable control points.
     */
    private void updateGradient() {
        // Start with the original image and render fresh gradient:
        Graphics2D graphics = gradientPreviewBuffer.createGraphics();
        try {
            graphics.drawImage(originalImage, 0, 0, null);
            drawGradient(graphics);
        }
        finally {
            graphics.dispose();
        }
        updateMarkerOverlayOnly();
    }

    /**
     * Re-renders only the point markers on top of the last gradient render. This is used while dragging
     * to keep interaction smooth and avoid recomputing the full gradient on each mouse move.
     */
    private void updateMarkerOverlayOnly() {
        // Figure out a size for our drag points so that they render
        // consistently regardless of image size:
        int markerDiameter = getMarkerDiameter();

        Graphics2D graphics = dBuffer.createGraphics();
        try {
            graphics.drawImage(gradientPreviewBuffer, 0, 0, null);
            drawStartPoint(graphics, markerDiameter);
            drawEndPoint(graphics, markerDiameter);
        }
        finally {
            graphics.dispose();
            imagePanel.setImage(dBuffer);
        }
    }

    /**
     * Renders the gradient with current settings to the given Graphics2D object.
     */
    private void drawGradient(Graphics2D g2d) {
        int startX = startXField.getCurrentValue().intValue();
        int startY = startYField.getCurrentValue().intValue();
        int endX = endXField.getCurrentValue().intValue();
        int endY = endYField.getCurrentValue().intValue();
        Color startColor = startColorField.getColor();
        Color endColor = endColorField.getColor();

        // Render a linear gradient from startColor to endColor between the two points:
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Point2D startPoint = new Point2D.Double(startX, startY);
        Point2D endPoint = new Point2D.Double(endX, endY);
        GradientPaint gradient = new GradientPaint(startPoint, startColor, endPoint, endColor);

        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, imgWidth, imgHeight);
    }

    /**
     * Renders the draggable gradient start point at its current location into the given Graphics2D object.
     */
    private void drawStartPoint(Graphics2D g2d, int diameter) {
        drawMarkerPoint(g2d,
                        startXField.getCurrentValue().intValue(),
                        startYField.getCurrentValue().intValue(),
                        diameter,
                        Color.GREEN);
    }

    /**
     * Renders the draggable gradient end point at its current location into the given Graphics2D object.
     */
    private void drawEndPoint(Graphics2D g2d, int diameter) {
        drawMarkerPoint(g2d,
                        endXField.getCurrentValue().intValue(),
                        endYField.getCurrentValue().intValue(),
                        diameter,
                        Color.RED);

    }

    /**
     * Draws a filled circle of the given color with a black border at the given location with the given diameter.
     */
    private void drawMarkerPoint(Graphics2D g2d, int x, int y, int diameter, Color fillColor) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setColor(fillColor);
        Ellipse2D.Double circle = new Ellipse2D.Double(x - diameter / 2.0, y - diameter / 2.0, diameter, diameter);
        g2d.fill(circle);

        g2d.setColor(Color.BLACK); // border
        g2d.setStroke(new BasicStroke(2));
        g2d.draw(circle);
    }

    private FormPanel buildControlPanel() {
        final int MAX = Integer.MAX_VALUE; // image dimensions aren't available at form build time, so...
        FormPanel formPanel = new FormPanel();
        formPanel.setBorderMargin(10);
        formPanel.setBorder(BorderFactory.createLoweredBevelBorder());

        final int headerSize = 16;
        formPanel.add(LabelField.createBoldHeaderLabel("Gradient:", headerSize));

        startColorField = new ColorField("Start color:", ColorSelectionType.SOLID);
        startColorField.setColor(Color.BLACK);
        startColorField.addValueChangedListener(_ -> onFieldValueChanged());
        formPanel.add(startColorField);

        endColorField = new ColorField("End color:", ColorSelectionType.SOLID);
        endColorField.setColor(new Color(0,0,0,0));
        endColorField.addValueChangedListener(_ -> onFieldValueChanged());
        formPanel.add(endColorField);

        startXField = new NumberField("Start X:", 0, 0, Integer.MAX_VALUE, 1);
        startXField.addValueChangedListener(_ -> onFieldValueChanged());
        formPanel.add(startXField);

        startYField = new NumberField("Start Y:", 0, 0, Integer.MAX_VALUE, 1);
        startYField.addValueChangedListener(_ -> onFieldValueChanged());
        formPanel.add(startYField);

        endXField = new NumberField("End X:", 0, 0, Integer.MAX_VALUE, 1);
        endXField.addValueChangedListener(_ -> onFieldValueChanged());
        formPanel.add(endXField);

        endYField = new NumberField("End Y:", 0, 0, Integer.MAX_VALUE, 1);
        endYField.addValueChangedListener(_ -> onFieldValueChanged());
        formPanel.add(endYField);

        PanelField wrapper = new PanelField(new GridBagLayout());
        wrapper.setShouldExpand(true);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 0, 4, 0);
        gbc.anchor = GridBagConstraints.CENTER;
        JPanel panel = wrapper.getPanel();
        JButton button = new JButton("Reset");
        button.addActionListener(e -> resetAll());
        button.setPreferredSize(new Dimension(150, 24));
        panel.add(button, gbc);

        gbc.gridy++;
        button = new JButton("Save and close");
        button.addActionListener(e -> saveChanges());
        button.setPreferredSize(new Dimension(150, 24));
        panel.add(button, gbc);

        gbc.gridy++;
        button = new JButton("Cancel");
        button.addActionListener(e -> dispose());
        button.setPreferredSize(new Dimension(150, 24));
        panel.add(button, gbc);

        panel.setBorder(BorderFactory.createLoweredBevelBorder());
        wrapper.getMargins().setTop(45);
        formPanel.add(wrapper);

        return formPanel;
    }

    private void loadImage() {
        try {
            originalImage = ImageUtil.loadImage(srcFile);
            imgWidth = originalImage.getWidth();
            imgHeight = originalImage.getHeight();
            // We create our dBuffer with an alpha channel even if the source image didn't have one:
            dBuffer = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
            gradientPreviewBuffer = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
            resetAll();
            updateGradient();
        }
        catch (IOException | ArrayIndexOutOfBoundsException ioe) {
            getMessageUtil().error("Error loading image: " + ioe.getMessage(), ioe);
            dispose();
        }
    }

    /**
     * As usual for dialogs, ESC will cancel the dialog, and Enter
     * will prompt to save the current edit and close the dialog.
     */
    private void configureKeyboardShortcuts() {
        keyStrokeManager.clear();
        keyStrokeManager.registerHandler("esc", e -> dispose());
        keyStrokeManager.registerHandler("enter", e -> promptToSaveAndExit());
    }

    private MessageUtil getMessageUtil() {
        if (messageUtil == null) {
            messageUtil = new MessageUtil(this, Logger.getLogger(GradientFillDialog.class.getName()));
        }
        return messageUtil;
    }

    private int getMarkerDiameter() {
        int markerDiameter = imgWidth / 75;
        return Math.max(markerDiameter, 10); // enforce a minimum marker size for very small images
    }

    private void onFieldValueChanged() {
        if (!suppressGradientRender) {
            updateGradient();
        }
    }

    private Point clampToImage(Point point) {
        int x = Math.max(0, Math.min(point.x, imgWidth - 1));
        int y = Math.max(0, Math.min(point.y, imgHeight - 1));
        return new Point(x, y);
    }

    private DragTarget getDragTargetAt(Point imagePoint) {
        int markerRadius = getMarkerDiameter() / 2;
        int radiusSquared = markerRadius * markerRadius;
        int startX = startXField.getCurrentValue().intValue();
        int startY = startYField.getCurrentValue().intValue();
        int endX = endXField.getCurrentValue().intValue();
        int endY = endYField.getCurrentValue().intValue();

        int startDx = imagePoint.x - startX;
        int startDy = imagePoint.y - startY;
        int endDx = imagePoint.x - endX;
        int endDy = imagePoint.y - endY;
        boolean inStart = (startDx * startDx + startDy * startDy) <= radiusSquared;
        boolean inEnd = (endDx * endDx + endDy * endDy) <= radiusSquared;
        if (inStart && inEnd) {
            return (startDx * startDx + startDy * startDy) <= (endDx * endDx + endDy * endDy)
                    ? DragTarget.START : DragTarget.END;
        }
        if (inStart) {
            return DragTarget.START;
        }
        if (inEnd) {
            return DragTarget.END;
        }
        return DragTarget.NONE;
    }

    private void applyPointToTarget(DragTarget target, Point imagePoint) {
        Point clamped = clampToImage(imagePoint);
        suppressGradientRender = true;
        try {
            if (target == DragTarget.START) {
                startXField.setCurrentValue(clamped.x);
                startYField.setCurrentValue(clamped.y);
            }
            else if (target == DragTarget.END) {
                endXField.setCurrentValue(clamped.x);
                endYField.setCurrentValue(clamped.y);
            }
        }
        finally {
            suppressGradientRender = false;
        }
    }

    /**
     * Idempotent cleanup method.
     */
    private void cleanup() {
        if (imagePanel != null) {
            imagePanel.dispose();
        }
        if (keyStrokeManager != null) {
            keyStrokeManager.dispose();
        }
        if (originalImage != null) {
            originalImage.flush();
            originalImage = null;
        }
        if (dBuffer != null) {
            dBuffer.flush();
            dBuffer = null;
        }
        if (gradientPreviewBuffer != null) {
            gradientPreviewBuffer.flush();
            gradientPreviewBuffer = null;
        }
    }

    /**
     * Simple cleanup class to ensure that our cleanup is invoked no matter how the window
     * is closed - either by our own close button, or by the user clicking the "X" button on
     * the window frame.
     */
    private class WindowCleanupListener extends WindowAdapter {
        @Override
        public void windowClosing(WindowEvent e) {
            cleanup();
        }

        @Override
        public void windowClosed(WindowEvent e) {
            cleanup();
        }
    }

    private class PointDragListener extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) {
                activeDragTarget = DragTarget.NONE;
                return;
            }
            Point translated = imagePanel.getTranslatedPoint(e.getPoint());
            activeDragTarget = getDragTargetAt(translated);
            if (activeDragTarget != DragTarget.NONE) {
                imagePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                e.consume();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (activeDragTarget != DragTarget.NONE) {
                Point translated = imagePanel.getTranslatedPoint(e.getPoint());
                applyPointToTarget(activeDragTarget, translated);
                activeDragTarget = DragTarget.NONE;
                updateGradient();
            }
            else {
                activeDragTarget = DragTarget.NONE;
            }

            Point translated = imagePanel.getTranslatedPoint(e.getPoint());
            DragTarget hoverTarget = getDragTargetAt(translated);
            imagePanel.setCursor(hoverTarget == DragTarget.NONE
                                         ? Cursor.getDefaultCursor()
                                         : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (activeDragTarget == DragTarget.NONE) {
                return;
            }
            Point translated = imagePanel.getTranslatedPoint(e.getPoint());
            applyPointToTarget(activeDragTarget, translated);
            updateMarkerOverlayOnly();
            e.consume();
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            if (activeDragTarget != DragTarget.NONE) {
                imagePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                return;
            }

            Point translated = imagePanel.getTranslatedPoint(e.getPoint());
            DragTarget hoverTarget = getDragTargetAt(translated);
            imagePanel.setCursor(hoverTarget == DragTarget.NONE
                                         ? Cursor.getDefaultCursor()
                                         : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        public void mouseExited(MouseEvent e) {
            if (activeDragTarget == DragTarget.NONE) {
                imagePanel.setCursor(Cursor.getDefaultCursor());
            }
        }
    }
}
