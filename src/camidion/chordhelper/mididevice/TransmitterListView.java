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
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

/**
 * MIDIトランスミッタ（{@link Transmitter}）のリストビューです。
 * トランスミッタをこのビューからドラッグし、
 * {@link ReceiverListView} のレシーバにドロップして接続できます。
 */
public class TransmitterListView extends JList<Transmitter> {
	/**
	 * トランスミッタを描画するクラス
	 */
	private static class CellRenderer extends JLabel implements ListCellRenderer<Transmitter> {
		public Component getListCellRendererComponent(JList<? extends Transmitter> list,
				Transmitter value, int index, boolean isSelected, boolean cellHasFocus)
		{
			setEnabled(list.isEnabled());
			setFont(list.getFont());
			setOpaque(true);
			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			} else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}
			setIcon(MidiDeviceDialog.MIDI_CONNECTER_ICON);
			if( value instanceof DummyTransmitter ) {
				setToolTipText("未接続の送信端子(Tx)：ドラッグ＆ドロップしてRxに接続できます。");
			} else {
				setToolTipText("接続済の送信端子(Tx)：ドラッグ＆ドロップして接続先Rxを変更、または切断できます。");
			}
			return this;
		}
	}
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
	 * このリストによって表示される{@link Transmitter}のリストを保持するデータモデルを返します。
	 * @return 表示される{@link Transmitter}のリストを提供するデータモデル
	 */
	@Override
	public TransmitterListModel getModel() {
		return (TransmitterListModel) super.getModel();
	}
	/**
	 * 仮想MIDI端子リストビューを生成します。
	 * @param model このビューから参照されるデータモデル
	 * @param cablePane MIDIケーブル描画面
	 */
	public TransmitterListView(TransmitterListModel model, MidiCablePane cablePane) {
		super(model);
		setCellRenderer(new CellRenderer());
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setLayoutOrientation(JList.HORIZONTAL_WRAP);
		setVisibleRowCount(0);
		//
		// トランスミッタのドラッグを受け付ける
		DragGestureListener dgl = new DragGestureListener() {
			@Override
			public void dragGestureRecognized(DragGestureEvent event) {
				if( (event.getDragAction() & DnDConstants.ACTION_COPY_OR_MOVE) == 0 ) return;
				MidiCablePane.dragging.setData(getModel().getElementAt(locationToIndex(event.getDragOrigin())));
				event.startDrag(DragSource.DefaultLinkDrop, MidiCablePane.dragging, new DragSourceAdapter() {
					@Override
					public void dragDropEnd(DragSourceDropEvent event) {
						Transmitter droppedTx = (Transmitter)MidiCablePane.dragging.getData();
						if( ! event.getDropSuccess() ) {
							// 所定の場所にドロップされなかったトランスミッタを閉じる
							getModel().close(droppedTx);
						} else if( droppedTx instanceof DummyTransmitter ) {
							// ドロップされたダミートランスミッタに接続されたレシーバを
							// 新しい本物のトランスミッタに付け替える
							try {
								getModel().getTransmitter().setReceiver(droppedTx.getReceiver());
							} catch (Exception exception) {
								exception.printStackTrace();
							}
							droppedTx.setReceiver(null);
						}
						cablePane.dragSourceListener.dragDropEnd(event);
					}
				});
			}
		};
		DragSource dragSource = new DragSource();
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
				int maskedBits = event.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE;
				if( maskedBits == 0 ) {
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
						if( tx instanceof DummyTransmitter ) tx = getModel().getTransmitter();
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
