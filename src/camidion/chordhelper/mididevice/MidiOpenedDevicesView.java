package camidion.chordhelper.mididevice;

import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
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
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

/**
 * 開いている MIDI デバイスを置くためのデスクトップビュー
 */
public class MidiOpenedDevicesView extends JDesktopPane implements TreeSelectionListener {

	private Map<MidiTransceiverListModel, MidiDeviceFrame> modelToFrame = new HashMap<>();

	/**
	 * ツリー上で選択状態が変わったとき、このデスクトップ上のフレームの選択状態に反映します。
	 */
	@Override
	public void valueChanged(TreeSelectionEvent tse) {
		TreePath treePath = tse.getNewLeadSelectionPath();
		if( treePath != null ) {
			Object lastSelected = treePath.getLastPathComponent();
			if( lastSelected instanceof MidiTransceiverListModel ) {
				MidiTransceiverListModel deviceModel = (MidiTransceiverListModel)lastSelected;
				if( deviceModel.getMidiDevice().isOpen() ) {
					// 開いているMIDIデバイスがツリー上で選択されたら、対応するフレームを選択
					try {
						modelToFrame.get(deviceModel).setSelected(true);
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
	/**
	 * ツリー表示からこのデスクトップにドロップされたMIDIデバイスモデルに対応するフレームを表示するためのリスナー
	 */
	private DropTargetListener dropTargetListener = new DropTargetAdapter() {
		@Override
		public void dragEnter(DropTargetDragEvent dtde) {
			if( dtde.getTransferable().isDataFlavorSupported(MidiDeviceTreeView.LIST_MODEL_FLAVOR) ) {
				dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
			}
		}
		@Override
		public void drop(DropTargetDropEvent dtde) {
			dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
			try {
				int maskedBits = dtde.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE;
				if( maskedBits == 0 ) {
					dtde.dropComplete(false);
					return;
				}
				Transferable t = dtde.getTransferable();
				if( ! t.isDataFlavorSupported(MidiDeviceTreeView.LIST_MODEL_FLAVOR) ) {
					dtde.dropComplete(false);
					return;
				}
				Object data = t.getTransferData(MidiDeviceTreeView.LIST_MODEL_FLAVOR);
				if( ! (data instanceof MidiTransceiverListModel) ) {
					dtde.dropComplete(false);
					return;
				}
				MidiTransceiverListModel deviceModel = (MidiTransceiverListModel)data;
				try {
					deviceModel.getMidiDevice().open();
					deviceModel.openReceiver();
				} catch( MidiUnavailableException e ) {
					//
					// デバイスを開くのに失敗した場合
					//
					//   例えば、「Microsort MIDI マッパー」と
					//   「Microsoft GS Wavetable SW Synth」を
					//   同時に開こうとするとここに来る。
					//
					String title = "Cannot open MIDI device";
					String message = "MIDIデバイス "+deviceModel+" はすでに使用中です。\n"
						+"他のデバイスが連動して開いている可能性があります。\n\n"
						+ e.getMessage();
					dtde.dropComplete(false);
					JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
					return;
				}
				if( ! deviceModel.getMidiDevice().isOpen() ) {
					// 例外が出なかったにも関わらずデバイスが開かれていない場合
					String title = "Cannot open MIDI device";
					String message = "MIDIデバイス "+deviceModel+" はすでに使用中です。\n"
							+"他のデバイスが連動して開いている可能性があります。\n\n" ;
					dtde.dropComplete(false);
					JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
					return;
				}
				dtde.dropComplete(true);
				//
				// デバイスが正常に開かれたことを確認できたら
				// ドロップした場所へフレームを配置して可視化する。
				//
				MidiDeviceFrame deviceFrame = modelToFrame.get(deviceModel);
				deviceFrame.setLocation(dtde.getLocation());
				deviceFrame.setVisible(true);
			}
			catch (Exception ex) {
				ex.printStackTrace();
				dtde.dropComplete(false);
			}
		}
	};

	private MidiCablePane cablePane = new MidiCablePane(this);

	public MidiOpenedDevicesView(
			MidiDeviceTreeView deviceTreeView,
			MidiDeviceInfoPane deviceInfoPane,
			MidiDeviceDialog dialog
	) {
		add(cablePane, JLayeredPane.PALETTE_LAYER);
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) { cablePane.setSize(getSize()); }
		});
		int toX = 10;
		int toY = 10;
		MidiTransceiverListModelList deviceModels = deviceTreeView.getModel().getTransceiverListModelList();
		for( MidiTransceiverListModel deviceModel : deviceModels ) {
			MidiDeviceFrame frame = new MidiDeviceFrame(new MidiTransceiverListView(deviceModel, cablePane));
			modelToFrame.put(deviceModel, frame);
			//
			// MIDIデバイスモデルのMIDIコネクタリストが変化したときにMIDIケーブルを再描画
			deviceModel.addListDataListener(cablePane.midiConnecterListDataListener);
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
			frame.setSize(250, 90);
			add(frame);
			if( deviceModel.getMidiDevice().isOpen() ) {
				frame.setLocation(toX, toY);
				frame.setVisible(true);
				toX = (toX == 10 ? 270 : 10);
				toY += 50;
			}
		}
		new DropTarget( this, DnDConstants.ACTION_COPY_OR_MOVE, dropTargetListener, true );
	}

}
