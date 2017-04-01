package camidion.chordhelper.mididevice;

import java.beans.PropertyVetoException;

import javax.sound.midi.MidiDevice;
import javax.swing.JEditorPane;
import javax.swing.JInternalFrame;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

/**
 * MIDIデバイス情報表示エリア
 */
public class MidiDeviceInfoPane extends JEditorPane implements TreeSelectionListener {
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
			JInternalFrame frame = e.getInternalFrame();
			if( ! (frame instanceof MidiDeviceFrame ) ) return;
			setText(treeNodeTextOf(((MidiDeviceFrame)frame).getMidiDeviceModel()));
		}
		@Override
		public void internalFrameClosing(InternalFrameEvent e) {
			JInternalFrame ｆ = e.getInternalFrame();
			if( ! (ｆ instanceof MidiDeviceFrame ) ) return;
			MidiDeviceModel m = ((MidiDeviceFrame)ｆ).getMidiDeviceModel();
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
	 * ツリー上で選択状態が変わったとき、表示対象のMIDIデバイスを切り替えます。
	 */
	@Override
	public void valueChanged(TreeSelectionEvent e) {
		TreePath treePath = e.getNewLeadSelectionPath();
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
