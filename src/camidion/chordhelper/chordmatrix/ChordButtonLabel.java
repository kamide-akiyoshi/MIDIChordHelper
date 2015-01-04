package camidion.chordhelper.chordmatrix;

import java.awt.Font;

import javax.swing.JLabel;

/**
 * コードボタン用のマトリクス外のラベル
 */
public class ChordButtonLabel extends JLabel {
	private ChordMatrix cm;
	public ChordButtonLabel(String txt, ChordMatrix cm) {
		super(txt,CENTER);
		this.cm = cm;
		setOpaque(true);
		setFont(getFont().deriveFont(Font.PLAIN));
		setDarkMode(false);
	}
	public void setDarkMode(boolean isDark) {
		setBackground( isDark ?
			cm.darkModeColorset.backgrounds[2] :
			cm.normalModeColorset.backgrounds[2]
		);
		setForeground( isDark ?
			cm.darkModeColorset.foregrounds[0] :
			cm.normalModeColorset.foregrounds[0]
		);
	}
}
