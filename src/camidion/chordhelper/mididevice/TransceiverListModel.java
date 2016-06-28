package camidion.chordhelper.mididevice;

import java.util.List;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.AbstractListModel;

/**
 * {@link Transmitter}または{@link Receiver}のリストを表す{@link javax.swing.ListModel}の基底抽象クラス
 */
public abstract class TransceiverListModel<E> extends AbstractListModel<E> {
	public TransceiverListModel(MidiDeviceModel deviceModel) {
		this.deviceModel = deviceModel;
	}
	protected MidiDeviceModel deviceModel;
	protected abstract List<E> getTransceivers();
	@Override
	public E getElementAt(int index) { return getTransceivers().get(index); }
	@Override
	public int getSize() { return getTransceivers().size(); }
	/**
	 * 引数で指定された要素（{@link Transmitter}または{@link Receiver}）の位置を返します。
	 *
	 * @param element 要素
	 * @return 位置（0が先頭、見つからない場合 -1）
	 */
	public int indexOf(Object element) { return getTransceivers().indexOf(element); }
}
