package camidion.chordhelper.mididevice;

import java.beans.PropertyVetoException;

import javax.sound.midi.MidiDevice;
import javax.swing.JEditorPane;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.tree.TreePath;

/**
 * MIDIデバイス情報表示エリア
 */
public class MidiDeviceInfoPane extends JEditorPane {
	private String treeNodeTextOf(Object node) {
		String html = "<html><head></head><body>";
		if( node instanceof MidiDeviceModel ) {
			MidiDeviceModel deviceModel = (MidiDeviceModel)node;
			MidiDevice device = deviceModel.getMidiDevice();
			MidiDevice.Info info = device.getDeviceInfo();
			html += "<b>"+deviceModel+"</b><br/>"
				+ "<table border=\"1\"><tbody>"
				+ "<tr><th>Version</th><td>"+info.getVersion()+"</td></tr>"
				+ "<tr><th>Description</th><td>"+info.getDescription()+"</td></tr>"
				+ "<tr><th>Vendor</th><td>"+info.getVendor()+"</td></tr>"
				+ "<tr><th>Status</th><td>"+(device.isOpen()?"Opened":"Closed")+"</td></tr>"
				+ "</tbody></table>";
		}
		else if( node instanceof MidiDeviceInOutType ) {
			MidiDeviceInOutType ioType = (MidiDeviceInOutType)node;
			html += "<b>"+ioType+"</b><br/>"+ioType.getDescription()+"<br/>";
		}
		else if( node != null ) {
			html += node;
		}
		html += "</body></html>";
		return html;
	}
	/**
	 *	{@link MidiDeviceFrame} の開閉や選択を監視するリスナー
	 */
	public InternalFrameListener midiDeviceFrameListener = new InternalFrameAdapter() {
		@Override
		public void internalFrameOpened(InternalFrameEvent e) {
			internalFrameActivated(e);
		}
		@Override
		public void internalFrameActivated(InternalFrameEvent e) {
			setText(treeNodeTextOf(((MidiDeviceFrame)e.getInternalFrame()).getMidiDeviceModel()));
		}
		@Override
		public void internalFrameClosing(InternalFrameEvent e) {
			MidiDeviceFrame ｆ = (MidiDeviceFrame)e.getInternalFrame();
			MidiDeviceModel m = ｆ.getMidiDeviceModel();
			m.close();
			// デバイスが閉じたことを確認してから画面を閉じる
			if( ! m.getMidiDevice().isOpen() ) {
				try {
					// 選択されたまま閉じると、次に開いたときにinternalFrameActivatedが
					// 呼ばれなくなってしまうので、選択を解除する。
					ｆ.setSelected(false);
				} catch (PropertyVetoException pve) {
					pve.printStackTrace();
				}
				ｆ.setVisible(false);
			}
			setText(treeNodeTextOf(m));
		}
	};
	/**
	 * ツリーパスを設定し、その内容を表示します。
	 * @param treePath　表示するツリーパス
	 */
	public void setTreePath(TreePath treePath) {
		setText(treeNodeTextOf(treePath == null ? null : treePath.getLastPathComponent()));
	}
	/**
	 * MIDIデバイス情報表示エリアを構築します。
	 */
	public MidiDeviceInfoPane() {
        setContentType("text/html");
		setText(treeNodeTextOf(null));
		setEditable(false);
	}
}
