package ca.corbett.imageviewer.extensions.gradientfill;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.EnhancedAction;
import ca.corbett.extras.io.KeyStrokeManager;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.KeyStrokeProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;
import ca.corbett.imageviewer.ui.MainWindow;

import java.util.ArrayList;
import java.util.List;

/**
 * An ImageViewer extension that provides a dialog for applying a highly-configurable
 * gradient to images.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class GradientFillExtension extends ImageViewerExtension {
    private static final String keystrokeProp = AppConfig.KEYSTROKE_MISC_PREFIX + "gradientFill";
    private final AppExtensionInfo extInfo;

    public GradientFillExtension() {
        extInfo = AppExtensionInfo.fromExtensionJar(getClass(),
                                                    "/ca/corbett/imageviewer/extensions/gradientfill/extInfo.json");
        if (extInfo == null) {
            throw new RuntimeException("GradientFillExtension: can't parse extInfo.json!");
        }
    }

    @Override
    public AppExtensionInfo getInfo() {
        return extInfo;
    }

    @Override
    protected List<AbstractProperty> createConfigProperties() {
        List<AbstractProperty> props = new ArrayList<>();

        props.add(new KeyStrokeProperty(keystrokeProp,
                                        "Gradient fill:",
                                        KeyStrokeManager.parseKeyStroke("Ctrl+Shift+G"),
                                        GradientFillAction.getInstance())
                      .setAllowBlank(true)
                      .setReservedKeyStrokes(AppConfig.RESERVED_KEYSTROKES)
                      .setHelpText("Show the gradient fill dialog"));

        return props;
    }

    @Override
    public List<EnhancedAction> getMenuActions(String topLevelMenu, MainWindow.BrowseMode browseMode) {
        if ("Edit".equals(topLevelMenu)) {
            return List.of(GradientFillAction.getInstance());
        }
        return null;
    }

    @Override
    public List<EnhancedAction> getPopupMenuActions(MainWindow.BrowseMode browseMode) {
        return List.of(GradientFillAction.getInstance());
    }

    @Override
    protected void loadJarResources() {
        // Nothing to load here for this extension
    }
}
