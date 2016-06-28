package camidion.chordhelper.mididevice;

import java.awt.Point;
import java.awt.Rectangle;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.JList;
import javax.swing.ListSelectionModel;

/**
 * MIDIレシーバ（{@link Receiver}）またはMIDIトランスミッタ（{@link Transmitter}）のリストを表示する基底の抽象リストビューです。
 */
public abstract class TransceiverListView<E> extends JList<E> {
	/**
	 * このリストによって表示されるリストを保持するデータモデルを返します。
	 * @return 表示されるリストを提供するデータモデル
	 */
	@Override
	public TransceiverListModel<E> getModel() {
		return (TransceiverListModel<E>) super.getModel();
	}
	/**
	 * このリストの座標系内の指定された位置にある要素を返します。
	 * @param p 位置
	 */
	public E getElementAt(Point p) {
		return getModel().getElementAt(locationToIndex(p));
	}
	/**
	 * 引数で指定された{@link Receiver}または{@link Transmitter}のセル範囲を示す、
	 * リストの座標系内の境界の矩形を返します。対応するセルがない場合はnullを返します。
	 * @param trx {@link Receiver}または{@link Transmitter}
	 * @return セル範囲を示す境界の矩形、またはnull
	 */
	public Rectangle getCellBounds(E trx) {
		int index = getModel().indexOf(trx);
		return getCellBounds(index,index);
	}
	/**
	 * 仮想MIDI端子リストビューを生成します。
	 * @param model このビューから参照されるデータモデル
	 */
	public TransceiverListView(TransceiverListModel<E> model) {
		super(model);
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setLayoutOrientation(JList.HORIZONTAL_WRAP);
		setVisibleRowCount(0);
	}
}
