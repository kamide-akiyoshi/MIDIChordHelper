package camidion.chordhelper.chordmatrix;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

/**
 * コードサフィックスのヘルプ
 */
public class ChordGuide extends JPanel {
	private class ChordGuideLabel extends ChordButtonLabel {
		private JPopupMenu popupMenu = new JPopupMenu();
		public ChordGuideLabel(String txt, ChordMatrix cm) {
			super(txt,cm);
			addMouseListener(
				new MouseAdapter() {
					public void mousePressed(MouseEvent e) {
						popupMenu.show( e.getComponent(), 0, getHeight() );
					}
				}
			);
		}
		public void addMenu(JMenuItem menuItem) { popupMenu.add(menuItem); }
		public void addSeparator() { popupMenu.addSeparator(); }
	}
	private ChordGuideLabel guide76, guide5, guide9;
	public ChordGuide(ChordMatrix cm) {
		guide76 = new ChordGuideLabel(" 6  7  M7 ",cm) {
			{
				setToolTipText("How to add 7th, major 7th, 6th");
				addMenu(new JMenuItem("7        = <RightClick>"));
				addMenu(new JMenuItem("M7(maj7) = [Shift] <RightClick>"));
				addMenu(new JMenuItem("6        = [Shift]"));
			}
		};
		guide5 = new ChordGuideLabel(" -5 dim +5 aug ",cm){
			{
				setToolTipText("How to add -5, dim, +5, aug");
				addMenu(new JMenuItem("-5 (b5)      = [Alt]"));
				addMenu(new JMenuItem("+5 (#5/aug)  = [Alt] sus4"));
				addSeparator();
				addMenu(new JMenuItem("dim  (m-5)  = [Alt] minor"));
				addMenu(new JMenuItem("dim7 (m6-5) = [Alt] [Shift] minor"));
				addMenu(new JMenuItem("m7-5 = [Alt] minor <RightClick>"));
				addMenu(new JMenuItem("aug7 (7+5)  = [Alt] sus4 <RightClick>"));
			}
		};
		guide9 = new ChordGuideLabel(" add9 ",cm) {
			{
				setToolTipText("How to add 9th");
				addMenu(new JMenuItem("add9  = [Ctrl]"));
				addSeparator();
				addMenu(new JMenuItem("9     = [Ctrl] <RightClick>"));
				addMenu(new JMenuItem("M9    = [Ctrl] [Shift] <RightClick>"));
				addMenu(new JMenuItem("69    = [Ctrl] [Shift]"));
				addMenu(new JMenuItem("dim9  = [Ctrl] [Shift] [Alt] minor"));
			}
		};
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(guide76);
		add( Box.createHorizontalStrut(2) );
		add(guide5);
		add( Box.createHorizontalStrut(2) );
		add(guide9);
	}
	public void setDarkMode(boolean is_dark) {
		setBackground( is_dark ? Color.black : null );
		guide76.setDarkMode( is_dark );
		guide5.setDarkMode( is_dark );
		guide9.setDarkMode( is_dark );
	}
}