package camidion.chordhelper.mididevice;

import java.awt.Point;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.sound.midi.MidiUnavailableException;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.Timer;

/**
 * 開いている MIDI デバイスを置くためのデスクトップビュー
 */
public class MidiDesktopPane extends JDesktopPane implements DropTargetListener {
	private MidiCablePane cablePane = new MidiCablePane(this);
	public MidiDesktopPane(MidiDeviceTree deviceTree) {
		add(cablePane, JLayeredPane.PALETTE_LAYER);
		int i=0;
		MidiDeviceTreeModel treeModel = (MidiDeviceTreeModel)deviceTree.getModel();
		for( MidiConnecterListModel deviceModel : treeModel.deviceModelList ) {
			MidiDeviceFrame frame = new MidiDeviceFrame(deviceModel) {
				{
					addInternalFrameListener(cablePane);
					addComponentListener(cablePane);
				}
			};
			frame.addInternalFrameListener(deviceTree);
			deviceModel.addListDataListener(cablePane);
			add(frame);
			if( deviceModel.getMidiDevice().isOpen() ) {
				frame.setBounds( 10+(i%2)*260, 10+i*55, 250, 100 );
				frame.setVisible(true);
				i++;
			}
		}
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				cablePane.setSize(getSize());
			}
		});
		new DropTarget( this, DnDConstants.ACTION_COPY_OR_MOVE, this, true );
	}
	@Override
	public void dragEnter(DropTargetDragEvent dtde) {
		Transferable trans = dtde.getTransferable();
		if( trans.isDataFlavorSupported(MidiDeviceTree.TREE_MODEL_FLAVOR) ) {
			dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
		}
	}
	@Override
	public void dragExit(DropTargetEvent dte) {}
	@Override
	public void dragOver(DropTargetDragEvent dtde) {}
	@Override
	public void dropActionChanged(DropTargetDragEvent dtde) {}
	@Override
	public void drop(DropTargetDropEvent dtde) {
		dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
		try {
			int action = dtde.getDropAction() ;
			if( (action & DnDConstants.ACTION_COPY_OR_MOVE) != 0 ) {
				Transferable trans = dtde.getTransferable();
				Object data = trans.getTransferData(MidiDeviceTree.TREE_MODEL_FLAVOR);
				if( data instanceof MidiConnecterListModel ) {
					MidiConnecterListModel deviceModel = (MidiConnecterListModel)data;
					try {
						deviceModel.openDevice();
					} catch( MidiUnavailableException e ) {
						//
						// デバイスを開くのに失敗した場合
						//
						//   例えば、「Microsort MIDI マッパー」と
						//   「Microsoft GS Wavetable SW Synth」を
						//   同時に開こうとするとここに来る。
						//
						dtde.dropComplete(false);
						String message = "MIDIデバイス "
								+ deviceModel
								+" を開けません。\n"
								+ "すでに開かれているデバイスが"
								+ "このデバイスを連動して開いていないか確認してください。\n\n"
								+ e.getMessage();
						JOptionPane.showMessageDialog(
							null, message,
							"Cannot open MIDI device",
							JOptionPane.ERROR_MESSAGE
						);
						return;
					}
					if( deviceModel.getMidiDevice().isOpen() ) {
						dtde.dropComplete(true);
						//
						// デバイスが正常に開かれたことを確認できたら
						// ドロップした場所へフレームを配置して可視化する。
						//
						JInternalFrame frame = getFrameOf(deviceModel);
						if( frame != null ) {
							Point loc = dtde.getLocation();
							loc.translate( -frame.getWidth()/2, 0 );
							frame.setLocation(loc);
							frame.setVisible(true);
						}
						return;
					}
				}
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
		dtde.dropComplete(false);
	}
	/**
	 * 指定されたMIDIデバイスモデルに対するMIDIデバイスフレームを返します。
	 *
	 * @param deviceModel MIDIデバイスモデル
	 * @return 対応するMIDIデバイスフレーム（ない場合 null）
	 */
	public MidiDeviceFrame getFrameOf(MidiConnecterListModel deviceModel) {
		JInternalFrame[] frames = getAllFramesInLayer(JLayeredPane.DEFAULT_LAYER);
		for( JInternalFrame frame : frames ) {
			if( ! (frame instanceof MidiDeviceFrame) )
				continue;
			MidiDeviceFrame deviceFrame = (MidiDeviceFrame)frame;
			if( deviceFrame.listView.getModel() == deviceModel )
				return deviceFrame;
		}
		return null;
	}
	private boolean isTimerStarted;
	/**
	 * タイムスタンプを更新するタイマーを開始または停止します。
	 * @param toStart trueで開始、falseで停止
	 */
	public void setAllDeviceTimestampTimers(boolean toStart) {
		if( isTimerStarted == toStart ) return;
		isTimerStarted = toStart;
		JInternalFrame[] frames = getAllFramesInLayer(JLayeredPane.DEFAULT_LAYER);
		for( JInternalFrame frame : frames ) {
			if( ! (frame instanceof MidiDeviceFrame) )
				continue;
			MidiDeviceFrame deviceFrame = (MidiDeviceFrame)frame;
			Timer timer = deviceFrame.timer;
			if( toStart ) timer.start(); else timer.stop();
		}
	}
}