package camidion.chordhelper.mididevice;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.JList;
import javax.swing.ListSelectionModel;

/**
 * MIDIトランスミッタ（{@link Transmitter}）のリストビューです。
 * トランスミッタをこのビューからドラッグし、
 * {@link ReceiverListView} のレシーバにドロップして接続できます。
 */
public class TransmitterListView extends JList<Transmitter> {
	/**
	 * このリストによって表示される{@link Transmitter}のリストを保持するデータモデルを返します。
	 * @return 表示される{@link Transmitter}のリストを提供するデータモデル
	 */
	@Override
	public TransmitterListModel getModel() { return (TransmitterListModel) super.getModel(); }
	/**
	 * 引数で指定された{@link Transmitter}のセル範囲を示す、
	 * リストの座標系内の境界の矩形を返します。対応するセルがない場合はnullを返します。
	 * @return セル範囲を示す境界の矩形、またはnull
	 */
	public Rectangle getCellBounds(Transmitter tx) {
		int index = getModel().indexOf(tx);
		return getCellBounds(index,index);
	}
	/**
	 * 仮想MIDI端子リストビューを生成します。
	 * @param model このビューから参照されるデータモデル
	 * @param cablePane MIDIケーブル描画面
	 */
	public TransmitterListView(TransmitterListModel model, MidiCablePane cablePane) {
		super(model);
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setLayoutOrientation(JList.HORIZONTAL_WRAP);
		setVisibleRowCount(0);
		setCellRenderer(new TransceiverListCellRenderer<Transmitter>() {
			@Override
			public Component getListCellRendererComponent(JList<? extends Transmitter> list,
					Transmitter value, int index, boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if( value instanceof DummyTransmitter ) {
					setToolTipText("未接続の送信端子(Tx)：ドラッグ＆ドロップしてRxに接続できます。");
				} else {
					setToolTipText("接続済の送信端子(Tx)：ドラッグ＆ドロップして接続先Rxを変更、または切断できます。");
				}
				return this;
			}
		});
		DragSource dragSource = new DragSource();
		DragGestureListener dgl = new DragGestureListener() {
			@Override
			public void dragGestureRecognized(DragGestureEvent event) {
				if( (event.getDragAction() & DnDConstants.ACTION_COPY_OR_MOVE) == 0 ) return;
				Transmitter tx = getModel().getElementAt(locationToIndex(event.getDragOrigin()));
				MidiCablePane.dragging.setData(tx);
				event.startDrag(DragSource.DefaultLinkDrop, MidiCablePane.dragging, new DragSourceAdapter() {
					@Override
					public void dragDropEnd(DragSourceDropEvent event) {
						Transmitter tx = (Transmitter)MidiCablePane.dragging.getData();
						if( ! event.getDropSuccess() ) {
							getModel().closeTransmitter(tx);
						} else if( tx instanceof DummyTransmitter ) {
							Receiver rx = tx.getReceiver();
							tx.close();
							try {
								getModel().openTransmitter().setReceiver(rx);
							} catch (Exception exception) {
								exception.printStackTrace();
							}
						}
						cablePane.dragSourceListener.dragDropEnd(event);
					}
				});
			}
		};
		dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY_OR_MOVE, dgl);
		dragSource.addDragSourceMotionListener(cablePane.dragSourceMotionListener);
		//
		// 外からドラッグされたレシーバを、ドロップした場所のトランスミッタに接続する
		DropTargetListener dtl = new DropTargetAdapter() {
			@Override
			public void dragEnter(DropTargetDragEvent event) {
				if( event.isDataFlavorSupported(DraggingTransceiver.receiverFlavor) ) {
					event.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
				}
			}
			@Override
			public void drop(DropTargetDropEvent event) {
				event.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
				if( (event.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE) == 0 ) {
					event.dropComplete(false);
					return;
				}
				Transferable t = event.getTransferable();
				if( ! t.isDataFlavorSupported(DraggingTransceiver.receiverFlavor) ) {
					event.dropComplete(false);
					return;
				}
				try {
					Object rx = t.getTransferData(DraggingTransceiver.receiverFlavor);
					if( rx != null ) {
						Transmitter tx = getModel().getElementAt(locationToIndex(event.getLocation()));
						if( tx instanceof DummyTransmitter ) tx = getModel().openTransmitter();
						tx.setReceiver((Receiver)rx);
						event.dropComplete(true);
						return;
					}
				}
				catch (Exception ex) {
					ex.printStackTrace();
				}
				event.dropComplete(false);
			}
		};
		new DropTarget( this, DnDConstants.ACTION_COPY_OR_MOVE, dtl, true );
	}
}
