package camidion.chordhelper.mididevice;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyVetoException;
import java.util.HashMap;
import java.util.Map;

import javax.sound.midi.MidiUnavailableException;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.TransferHandler;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

/**
 * 開いているMIDIデバイスを置くためのデスクトップビュー
 */
public class MidiOpenedDevicesView extends JDesktopPane implements TreeSelectionListener {
	/**
	 * MIDIデバイスモデルからフレームを割り出すためのマップ
	 */
	private Map<MidiDeviceModel, MidiDeviceFrame> modelToFrame = new HashMap<>();
	/**
	 * ツリー上で選択状態が変わったとき、このデスクトップ上のフレームの選択状態に反映します。
	 */
	@Override
	public void valueChanged(TreeSelectionEvent tse) {
		TreePath treePath = tse.getNewLeadSelectionPath();
		if( treePath != null ) {
			Object lastSelected = treePath.getLastPathComponent();
			if( lastSelected instanceof MidiDeviceModel ) {
				MidiDeviceModel deviceModel = (MidiDeviceModel)lastSelected;
				if( deviceModel.getMidiDevice().isOpen() ) {
					// 開いているMIDIデバイスがツリー上で選択されたら、対応するフレームを選択
					MidiDeviceFrame deviceFrame = modelToFrame.get(deviceModel);
					deviceFrame.toFront();
					try {
						deviceFrame.setSelected(true);
					} catch( PropertyVetoException ex ) {
						ex.printStackTrace();
					}
					return;
				}
			}
		}
		// それ以外が選択されたら、現在選択されているフレームを非選択
		JInternalFrame frame = getSelectedFrame();
		if( ! (frame instanceof MidiDeviceFrame) ) return;
		try {
			((MidiDeviceFrame)frame).setSelected(false);
		} catch( PropertyVetoException ex ) {
			ex.printStackTrace();
		}
	}

	public MidiOpenedDevicesView(MidiDeviceTreeView deviceTreeView,
			MidiDeviceInfoPane deviceInfoPane, MidiDeviceDialog dialog)
	{
		MidiCablePane cablePane = new MidiCablePane(this);
		add(cablePane, JLayeredPane.PALETTE_LAYER);
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				cablePane.setSize(getSize());
				cablePane.repaint();
			}
			@Override
			public void componentShown(ComponentEvent e) { cablePane.repaint(); }
		});
		int toX = 10;
		int toY = 10;
		MidiDeviceModelList deviceModels = deviceTreeView.getModel().getDeviceModelList();
		for( MidiDeviceModel deviceModel : deviceModels ) {
			MidiDeviceFrame frame = new MidiDeviceFrame(deviceModel, cablePane);
			modelToFrame.put(deviceModel, frame);
			//
			// トランスミッタリストモデルが変化したときにMIDIケーブルを再描画
			TransmitterListModel txListModel = deviceModel.getTransmitterListModel();
			if( txListModel != null ) txListModel.addListDataListener(cablePane.midiConnecterListDataListener);
			//
			// レシーバリストモデルが変化したときにMIDIケーブルを再描画
			ReceiverListModel rxListModel = deviceModel.getReceiverListModel();
			if( rxListModel != null ) rxListModel.addListDataListener(cablePane.midiConnecterListDataListener);
			//
			// デバイスフレームが開閉したときの動作
			frame.addInternalFrameListener(cablePane.midiDeviceFrameListener);
			frame.addInternalFrameListener(deviceTreeView.midiDeviceFrameListener);
			frame.addInternalFrameListener(deviceInfoPane.midiDeviceFrameListener);
			//
			// 移動または変形時の動作
			frame.addComponentListener(cablePane.midiDeviceFrameComponentListener);
			//
			// ダイアログが閉じたときの動作
			dialog.addWindowListener(frame.windowListener);
			//
			frame.setSize(250, deviceModel.getMidiDeviceInOutType() == MidiDeviceInOutType.MIDI_IN_OUT ? 90 : 70);
			add(frame);
			if( deviceModel.getMidiDevice().isOpen() ) {
				frame.setLocation(toX, toY);
				frame.setVisible(true);
				toX = (toX == 10 ? 270 : 10);
				toY += 50;
			}
		}
		setTransferHandler(new TransferHandler() {
			@Override
			public boolean canImport(TransferSupport support) {
				if( ! support.isDrop() ) return false;
				if( support.isDataFlavorSupported(MidiDeviceTreeView.deviceModelFlavor) ) {
					// MIDIデバイスを開くためのドロップを受け付ける
					return true;
				}
				if( support.isDataFlavorSupported(TransmitterListView.elementFlavor) ) {
					cablePane.setDragDestinationTransceiver(null);
					// Transmitterの切り離しができるよう、ドロップを容認
					return true;
				}
				if( support.isDataFlavorSupported(ReceiverListView.elementFlavor) ) {
					cablePane.setDragDestinationTransceiver(null);
					// Receiverはドロップ不可
				}
				return false;
			}
			@Override
			public boolean importData(TransferSupport support) {
				MidiDeviceModel deviceModel = null;
				Object from;
				try {
					from = support.getTransferable().getTransferData(MidiDeviceTreeView.deviceModelFlavor);
					if( ! (from instanceof MidiDeviceModel) ) return false;
					deviceModel = (MidiDeviceModel)from;
					deviceModel.open();
					if( ! deviceModel.getMidiDevice().isOpen() ) {
						throw new MidiUnavailableException("開いたはずなのに、開かれた状態になっていません。");
					}
					MidiDeviceFrame deviceFrame = modelToFrame.get(deviceModel);
					if( ! deviceFrame.isVisible() ) {
						deviceFrame.setLocation(support.getDropLocation().getDropPoint());
						deviceFrame.setVisible(true);
					}
					return true;
				} catch( MidiUnavailableException e ) {
					//
					// デバイスを開くのに失敗した場合
					//
					//   例えば、「Microsort MIDI マッパー」と「Microsoft GS Wavetable SW Synth」は
					//   連動して開かれるため、同時に開こうとすると、ここにたどり着く。
					//
					String title = "Cannot open MIDI device";
					String message = "MIDIデバイス "+deviceModel+" はすでに使用中です。\n"
						+"他のデバイスが連動して開いている可能性があります。\n\n"
						+ e.getMessage();
					JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
					return false;
				} catch (Exception ex) {
					ex.printStackTrace();
					return false;
				}
			}
		});
	}

}
