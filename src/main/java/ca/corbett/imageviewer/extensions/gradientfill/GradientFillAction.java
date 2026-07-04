package ca.corbett.imageviewer.extensions.gradientfill;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;

import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Locale;

/**
 * Launches the GradientFillDialog based on the currently selected image, if there is one.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class GradientFillAction extends EnhancedAction {

    private static GradientFillAction instance;
    private static final String NAME = "Gradient fill...";

    private GradientFillAction() {
        super(NAME); // no icon for this action
    }

    public static GradientFillAction getInstance() {
        if (instance == null) {
            instance = new GradientFillAction();
        }
        return instance;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ImageInstance currentImage = MainWindow.getInstance().getSelectedImage();
        if (currentImage.isEmpty()) {
            MainWindow.getInstance().showMessageDialog("Gradient fill", "Nothing selected.");
            return;
        }

        // Ensure correct file format:
        File file = currentImage.getImageFile();
        String filename = file.getName().toLowerCase(Locale.ROOT);
        if (!filename.endsWith("jpg")
            && !filename.endsWith("jpeg")
            && !filename.endsWith("png")) {
            MainWindow.getInstance().showMessageDialog("Gradient fill",
                                                       "Gradient fill can currently only be performed on jpeg or png images.");
            return;
        }

        new GradientFillDialog(file).setVisible(true);
    }
}
