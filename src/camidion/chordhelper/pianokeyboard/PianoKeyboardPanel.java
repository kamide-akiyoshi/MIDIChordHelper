package camidion.chordhelper.pianokeyboard;

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;

public class PianoKeyboardPanel extends JPanel {
	public PianoKeyboardPanel() {
		keyboard = new PianoKeyboard() {
			{
				addPianoKeyboardListener(new PianoKeyboardAdapter() {
					@Override
					public void octaveResized(ChangeEvent e) {
						octaveSelecter.setBlockIncrement(getOctaves());
					}
				});
			}
		};
		octaveSelecter = new JScrollBar(JScrollBar.HORIZONTAL) {
			{
				setToolTipText("Octave position");
				setModel(keyboard.octaveRangeModel);
				setBlockIncrement(keyboard.getOctaves());
			}
		};
		octaveSizeSlider = new JSlider() {
			{
				setToolTipText("Octave size");
				setModel(keyboard.octaveSizeModel);
				setMinimumSize(new Dimension(100, 18));
				setMaximumSize(new Dimension(100, 18));
				setPreferredSize(new Dimension(100, 18));
			}
		};
		octaveBar = new JPanel() {
			{
				setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
				add(octaveSelecter);
				add(Box.createHorizontalStrut(5));
				add(octaveSizeSlider);
			}
		};
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		add(octaveBar);
		add(keyboard);
		setAlignmentX((float)0.5);
	}
	public PianoKeyboard keyboard;
	private JScrollBar octaveSelecter;
	private JSlider	octaveSizeSlider;
	private JPanel octaveBar;
	public void setDarkMode(boolean isDark) {
		Color col = isDark ? Color.black : null;
		octaveSelecter.setBackground( col );
		octaveSizeSlider.setBackground( col );
		octaveBar.setBackground( col );
		keyboard.setDarkMode( isDark );
	}
}