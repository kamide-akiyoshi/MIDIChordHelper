package camidion.chordhelper;

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.event.HyperlinkEvent;

import camidion.chordhelper.ChordHelperApplet.VersionInfo;
/**
 * バージョン情報表示欄
 */
public class AboutMessagePane extends JEditorPane {
	private Action openAction;
	/**
	 * このバージョン情報表示欄を開くためのアクションを返します。
	 */
	public Action getOpenAction() { return openAction; }
	/**
	 * バージョン情報表示欄を構築します。
	 * @param imageIcon 画像アイコン
	 */
	public AboutMessagePane(ImageIcon imageIcon) {
		super("text/html", "");
		openAction = new AbstractAction() {
			{
				putValue(NAME, "Version info");
				putValue(SHORT_DESCRIPTION, VersionInfo.NAME + " " + VersionInfo.VERSION);
			}
			@Override
			public void actionPerformed(ActionEvent e) {
				JOptionPane.showMessageDialog(
					null, AboutMessagePane.this, getValue(NAME).toString(),
					JOptionPane.INFORMATION_MESSAGE, imageIcon
				);
			}
		};
		boolean linkEnabled = Desktop.isDesktopSupported();
		String linkString = VersionInfo.URL;
		String tooltip = null;
		if( linkEnabled ) {
			tooltip = "Click to open on web browser - Webブラウザで開く";
			linkString = "<a href=\""+linkString+"\" title=\""+tooltip+"\">"+linkString+"</a>";
		}
		setText("<html><center><font size=\"+1\">" +
				VersionInfo.NAME + "</font> " +
				VersionInfo.VERSION + "<br/><br/>" +
				VersionInfo.COPYRIGHT + " " +
				VersionInfo.AUTHER + "<br/>" +
				linkString + "</center></html>");
		setToolTipText(tooltip);
		setOpaque(false);
		putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		setEditable(false);
		if( ! linkEnabled ) return;
		URI uri;
		try {
			uri = new URI(VersionInfo.URL);
		}catch( URISyntaxException use ) {
			use.printStackTrace();
			return;
		}
		addHyperlinkListener(e->{
			if( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED ) {
				try{
					Desktop.getDesktop().browse(uri);
				}catch(IOException ioe) {
					ioe.printStackTrace();
				}
			}
		});
	}
}