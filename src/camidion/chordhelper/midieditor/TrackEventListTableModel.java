package camidion.chordhelper.midieditor;

import java.nio.charset.Charset;
import java.util.Vector;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.swing.DefaultListSelectionModel;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

import camidion.chordhelper.music.MIDISpec;

/**
 * MIDIトラック（MIDIイベントリスト）テーブルモデル
 */
public class TrackEventListTableModel extends AbstractTableModel {
	/**
	 * 列
	 */
	public enum Column {
		/** MIDIイベント番号 */
		EVENT_NUMBER("#", Integer.class, 15) {
			@Override
			public boolean isCellEditable() { return false; }
		},
		/** tick位置 */
		TICK_POSITION("TickPos.", Long.class, 40) {
			@Override
			public Object getValue(MidiEvent event) {
				return event.getTick();
			}
		},
		/** tick位置に対応する小節 */
		MEASURE_POSITION("Measure", Integer.class, 30) {
			public Object getValue(SequenceTrackListTableModel seq, MidiEvent event) {
				return seq.getSequenceTickIndex().tickToMeasure(event.getTick()) + 1;
			}
		},
		/** tick位置に対応する拍 */
		BEAT_POSITION("Beat", Integer.class, 20) {
			@Override
			public Object getValue(SequenceTrackListTableModel seq, MidiEvent event) {
				SequenceTickIndex sti = seq.getSequenceTickIndex();
				sti.tickToMeasure(event.getTick());
				return sti.lastBeat + 1;
			}
		},
		/** tick位置に対応する余剰tick（拍に収まらずに余ったtick数） */
		EXTRA_TICK_POSITION("ExTick", Integer.class, 20) {
			@Override
			public Object getValue(SequenceTrackListTableModel seq, MidiEvent event) {
				SequenceTickIndex sti = seq.getSequenceTickIndex();
				sti.tickToMeasure(event.getTick());
				return sti.lastExtraTick;
			}
		},
		/** MIDIメッセージ */
		MESSAGE("MIDI Message", String.class, 300) {
			@Override
			public Object getValue(SequenceTrackListTableModel seq, MidiEvent event) {
				return MIDISpec.msgToString(event.getMessage(), seq.charset);
			}
		};
		private String title;
		private Class<?> columnClass;
		int preferredWidth;
		/**
		 * 列の識別子を構築します。
		 * @param title 列のタイトル
		 * @param widthRatio 幅の割合
		 * @param columnClass 列のクラス
		 * @param perferredWidth 列の適切な幅
		 */
		private Column(String title, Class<?> columnClass, int preferredWidth) {
			this.title = title;
			this.columnClass = columnClass;
			this.preferredWidth = preferredWidth;
		}
		/**
		 * セルを編集できるときtrue、編集できないときfalseを返します。
		 */
		public boolean isCellEditable() { return true; }
		/**
		 * 列の値を返します。
		 * @param event 対象イベント
		 * @return この列の対象イベントにおける値
		 */
		public Object getValue(MidiEvent event) { return ""; }
		/**
		 * 列の値を返します。
		 * @param sti 対象シーケンスモデル
		 * @param event 対象イベント
		 * @return この列の対象イベントにおける値
		 */
		public Object getValue(SequenceTrackListTableModel seq, MidiEvent event) {
			return getValue(event);
		}
	}
	/**
	 * ラップされているMIDIトラック
	 */
	private Track track;
	private SequenceTrackListTableModel sequenceTrackListTableModel;
	/**
	 * このトラックモデルを収容している親のシーケンスモデルを返します。
	 */
	public SequenceTrackListTableModel getParent() {
		return sequenceTrackListTableModel;
	}
	private ListSelectionModel eventSelectionModel = new DefaultListSelectionModel() {
		{
			setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		}
	};
	/**
	 * 選択状態を返します。
	 */
	public ListSelectionModel getSelectionModel() { return eventSelectionModel; }
	/**
	 * シーケンスを親にして、その特定のトラックに連動する
	 * MIDIトラックモデルを構築します。
	 *
	 * @param parent 親のシーケンスモデル
	 * @param track ラップするMIDIトラック（ない場合はnull）
	 */
	public TrackEventListTableModel(
		SequenceTrackListTableModel sequenceTrackListTableModel, Track track
	) {
		this.track = track;
		this.sequenceTrackListTableModel = sequenceTrackListTableModel;
	}
	@Override
	public int getRowCount() { return track == null ? 0 : track.size(); }
	@Override
	public int getColumnCount() { return Column.values().length; }
	/**
	 * 列名を返します。
	 */
	@Override
	public String getColumnName(int column) {
		return Column.values()[column].title;
	}
	/**
	 * 列のクラスを返します。
	 */
	@Override
	public Class<?> getColumnClass(int column) {
		return Column.values()[column].columnClass;
	}
	@Override
	public Object getValueAt(int row, int column) {
		TrackEventListTableModel.Column c = Column.values()[column];
		if( c == Column.EVENT_NUMBER ) return row;
		MidiEvent event = track.get(row);
		switch(c) {
		case MEASURE_POSITION:
		case BEAT_POSITION:
		case EXTRA_TICK_POSITION:
		case MESSAGE: return c.getValue(sequenceTrackListTableModel, event);
		default: return c.getValue(event);
		}
	}
	/**
	 * セルを編集できるときtrue、編集できないときfalseを返します。
	 */
	@Override
	public boolean isCellEditable(int row, int column) {
		return Column.values()[column].isCellEditable();
	}
	/**
	 * セルの値を変更します。
	 */
	@Override
	public void setValueAt(Object value, int row, int column) {
		long newTick;
		switch(Column.values()[column]) {
		case TICK_POSITION: newTick = (Long)value; break;
		case MEASURE_POSITION:
			newTick = sequenceTrackListTableModel.getSequenceTickIndex().measureToTick(
				(Integer)value - 1,
				(Integer)getValueAt( row, Column.BEAT_POSITION.ordinal() ) - 1,
				(Integer)getValueAt( row, Column.EXTRA_TICK_POSITION.ordinal() )
			);
			break;
		case BEAT_POSITION:
			newTick = sequenceTrackListTableModel.getSequenceTickIndex().measureToTick(
				(Integer)getValueAt( row, Column.MEASURE_POSITION.ordinal() ) - 1,
				(Integer)value - 1,
				(Integer)getValueAt( row, Column.EXTRA_TICK_POSITION.ordinal() )
			);
			break;
		case EXTRA_TICK_POSITION:
			newTick = sequenceTrackListTableModel.getSequenceTickIndex().measureToTick(
				(Integer)getValueAt( row, Column.MEASURE_POSITION.ordinal() ) - 1,
				(Integer)getValueAt( row, Column.BEAT_POSITION.ordinal() ) - 1,
				(Integer)value
			);
			break;
		default: return;
		}
		MidiEvent oldMidiEvent = track.get(row);
		if( oldMidiEvent.getTick() == newTick ) return;
		MidiEvent newMidiEvent = new MidiEvent(oldMidiEvent.getMessage(), newTick);
		track.remove(oldMidiEvent);
		track.add(newMidiEvent);
		fireTableDataChanged();
		sequenceTrackListTableModel.setModified(true);
	}
	/**
	 * MIDIトラックを返します。
	 * @return MIDIトラック
	 */
	public Track getTrack() { return track; }
	/**
	 * トラック名を返します。
	 */
	@Override
	public String toString() {
		byte b[] = MIDISpec.getNameBytesOf(track);
		if( b == null ) return "";
		Charset cs = Charset.defaultCharset();
		if( sequenceTrackListTableModel != null ) cs = sequenceTrackListTableModel.charset;
		return new String(b, cs);
	}
	/**
	 * トラック名を設定します。
	 * @param name トラック名
	 * @return 設定が行われたらtrue
	 */
	public boolean setString(String name) {
		if( name.equals(toString()) || ! MIDISpec.setNameBytesOf(
			track, name.getBytes(sequenceTrackListTableModel.charset))
		) return false;
		sequenceTrackListTableModel.setModified(true);
		fireTableDataChanged();
		return true;
	}
	private String recordingChannel = "OFF";
	/**
	 * 録音中のMIDIチャンネルを返します。
	 * @return 録音中のMIDIチャンネル
	 */
	public String getRecordingChannel() { return recordingChannel; }
	/**
	 * 録音中のMIDIチャンネルを設定します。
	 * @param recordingChannel 録音中のMIDIチャンネル
	 */
	public void setRecordingChannel(String recordingChannel) {
		Sequencer sequencer = sequenceTrackListTableModel.getParent().getSequencerModel().getSequencer();
		if( recordingChannel.equals("OFF") ) sequencer.recordDisable(track);
		else if( recordingChannel.equals("ALL") ) sequencer.recordEnable(track,-1);
		else try {
			sequencer.recordEnable(track, Integer.decode(recordingChannel).intValue()-1);
		} catch( NumberFormatException nfe ) {
			sequencer.recordDisable(track);
			this.recordingChannel = "OFF";
			return;
		}
		this.recordingChannel = recordingChannel;
	}
	/**
	 * このトラックの対象MIDIチャンネルを返します。
	 * <p>全てのチャンネルメッセージが同じMIDIチャンネルの場合、
	 * そのMIDIチャンネルを返します。
	 * MIDIチャンネルの異なるチャンネルメッセージが一つでも含まれていた場合、
	 * -1 を返します。
	 * </p>
	 * @return 対象MIDIチャンネル（不統一の場合 -1）
	 */
	public int getChannel() {
		int prevCh = -1;
		int trackSize = track.size();
		for( int index=0; index < trackSize; index++ ) {
			MidiMessage msg = track.get(index).getMessage();
			if( ! (msg instanceof ShortMessage) ) continue;
			ShortMessage smsg = (ShortMessage)msg;
			if( ! MIDISpec.isChannelMessage(smsg) ) continue;
			int ch = smsg.getChannel();
			if( prevCh >= 0 && prevCh != ch ) return -1;
			prevCh = ch;
		}
		return prevCh;
	}
	/**
	 * 指定されたMIDIチャンネルをすべてのチャンネルメッセージに対して設定します。
	 * @param channel MIDIチャンネル
	 */
	public void setChannel(int channel) {
		boolean isModified = false;
		int trackSize = track.size();
		for( int index=0; index < trackSize; index++ ) {
			MidiMessage m = track.get(index).getMessage();
			if( ! (m instanceof ShortMessage) ) continue;
			ShortMessage sm = (ShortMessage)m;
			if( ! MIDISpec.isChannelMessage(sm) || sm.getChannel() == channel ) continue;
			try {
				sm.setMessage(sm.getCommand(), channel, sm.getData1(), sm.getData2());
				isModified = true;
			}
			catch( InvalidMidiDataException e ) {
				e.printStackTrace();
			}
		}
		if( isModified ) {
			sequenceTrackListTableModel.fireTrackChanged(track);
			fireTableDataChanged();
		}
	}
	/**
	 * 指定の MIDI tick 位置にあるイベントを二分探索し、
	 * そのイベントの行インデックスを返します。
	 * @param tick MIDI tick
	 * @return 行インデックス
	 */
	public int tickToIndex(long tick) {
		if( track == null ) return 0;
		int minIndex = 0;
		int maxIndex = track.size() - 1;
		while( minIndex < maxIndex ) {
			int currentIndex = (minIndex + maxIndex) / 2 ;
			long currentTick = track.get(currentIndex).getTick();
			if( tick > currentTick ) minIndex = currentIndex + 1;
			else if( tick < currentTick ) maxIndex = currentIndex - 1;
			else return currentIndex;
		}
		return (minIndex + maxIndex) / 2;
	}
	/**
	 * NoteOn/NoteOff ペアの一方の行インデックスから、
	 * もう一方（ペアの相手）の行インデックスを返します。
	 * @param index 行インデックス
	 * @return ペアを構成する相手の行インデックス（ない場合は -1）
	 */
	public int getIndexOfPartnerFor(int index) {
		if( track == null || index >= track.size() ) return -1;
		MidiMessage msg = track.get(index).getMessage();
		if( ! (msg instanceof ShortMessage) ) return -1;
		ShortMessage sm = (ShortMessage)msg;
		int cmd = sm.getCommand();
		int i;
		int ch = sm.getChannel();
		int note = sm.getData1();
		MidiMessage partner_msg;
		ShortMessage partner_sm;
		int partner_cmd;

		switch( cmd ) {
		case 0x90: // NoteOn
		if( sm.getData2() > 0 ) {
			// Search NoteOff event forward
			for( i = index + 1; i < track.size(); i++ ) {
				partner_msg = track.get(i).getMessage();
				if( ! (partner_msg instanceof ShortMessage ) ) continue;
				partner_sm = (ShortMessage)partner_msg;
				partner_cmd = partner_sm.getCommand();
				if( partner_cmd != 0x80 && partner_cmd != 0x90 ||
						partner_cmd == 0x90 && partner_sm.getData2() > 0
						) {
					// Not NoteOff
					continue;
				}
				if( ch != partner_sm.getChannel() || note != partner_sm.getData1() ) {
					// Not my partner
					continue;
				}
				return i;
			}
			break;
		}
		// When velocity is 0, it means Note Off, so no break.
		case 0x80: // NoteOff
			// Search NoteOn event backward
			for( i = index - 1; i >= 0; i-- ) {
				partner_msg = track.get(i).getMessage();
				if( ! (partner_msg instanceof ShortMessage ) ) continue;
				partner_sm = (ShortMessage)partner_msg;
				partner_cmd = partner_sm.getCommand();
				if( partner_cmd != 0x90 || partner_sm.getData2() <= 0 ) {
					// Not NoteOn
					continue;
				}
				if( ch != partner_sm.getChannel() || note != partner_sm.getData1() ) {
					// Not my partner
					continue;
				}
				return i;
			}
			break;
		}
		// Not found
		return -1;
	}
	/**
	 * ノートメッセージかどうか調べます。
	 * @param index 行インデックス
	 * @return Note On または Note Off のとき true
	 */
	public boolean isNote(int index) {
		MidiEvent midiEvent = getMidiEvent(index);
		MidiMessage msg = midiEvent.getMessage();
		if( ! (msg instanceof ShortMessage) ) return false;
		int cmd = ((ShortMessage)msg).getCommand();
		return cmd == ShortMessage.NOTE_ON || cmd == ShortMessage.NOTE_OFF ;
	}
	/**
	 * 指定の行インデックスのMIDIイベントを返します。
	 * @param index 行インデックス
	 * @return MIDIイベント
	 */
	public MidiEvent getMidiEvent(int index) {
		return track==null ? null : track.get(index);
	}
	/**
	 * 選択されているMIDIイベントを返します。
	 * @return 選択されているMIDIイベント
	 */
	public MidiEvent[] getSelectedMidiEvents() {
		Vector<MidiEvent> events = new Vector<MidiEvent>();
		if( ! eventSelectionModel.isSelectionEmpty() ) {
			int i = eventSelectionModel.getMinSelectionIndex();
			int max = eventSelectionModel.getMaxSelectionIndex();
			for( ; i <= max; i++ )
				if( eventSelectionModel.isSelectedIndex(i) )
					events.add(track.get(i));
		}
		return events.toArray(new MidiEvent[1]);
	}
	/**
	 * MIDIイベントを追加します。
	 * @param midiEvent 追加するMIDIイベント
	 * @return 追加できたらtrue
	 */
	public boolean addMidiEvent(MidiEvent midiEvent) {
		if( track == null || !(track.add(midiEvent)) )
			return false;
		if( MIDISpec.isTimeSignature(midiEvent.getMessage()) )
			sequenceTrackListTableModel.fireTimeSignatureChanged();
		sequenceTrackListTableModel.fireTrackChanged(track);
		int lastIndex = track.size() - 1;
		fireTableRowsInserted( lastIndex-1, lastIndex-1 );
		return true;
	}
	/**
	 * MIDIイベントを追加します。
	 * @param midiEvents 追加するMIDIイベント
	 * @param destinationTick 追加先tick
	 * @param sourcePPQ PPQ値（タイミング解像度）
	 * @return 追加できたらtrue
	 */
	public boolean addMidiEvents(MidiEvent midiEvents[], long destinationTick, int sourcePPQ) {
		if( track == null ) return false;
		int destinationPPQ = sequenceTrackListTableModel.getSequence().getResolution();
		boolean done = false;
		boolean hasTimeSignature = false;
		long firstSourceEventTick = -1;
		for( MidiEvent sourceEvent : midiEvents ) {
			long sourceEventTick = sourceEvent.getTick();
			MidiMessage msg = sourceEvent.getMessage();
			long newTick = destinationTick;
			if( firstSourceEventTick < 0 ) {
				firstSourceEventTick = sourceEventTick;
			}
			else {
				newTick += (sourceEventTick - firstSourceEventTick) * destinationPPQ / sourcePPQ;
			}
			if( ! track.add(new MidiEvent(msg, newTick)) ) continue;
			done = true;
			if( MIDISpec.isTimeSignature(msg) ) hasTimeSignature = true;
		}
		if( done ) {
			if( hasTimeSignature ) sequenceTrackListTableModel.fireTimeSignatureChanged();
			sequenceTrackListTableModel.fireTrackChanged(track);
			int lastIndex = track.size() - 1;
			int oldLastIndex = lastIndex - midiEvents.length;
			fireTableRowsInserted(oldLastIndex, lastIndex);
		}
		return done;
	}
	/**
	 * MIDIイベントを除去します。
	 * @param midiEvents 除去するMIDIイベント
	 */
	public void removeMidiEvents(MidiEvent midiEvents[]) {
		if( track == null ) return;
		boolean hadTimeSignature = false;
		for( MidiEvent e : midiEvents ) {
			if( MIDISpec.isTimeSignature(e.getMessage()) ) hadTimeSignature = true;
			track.remove(e);
		}
		if( hadTimeSignature ) {
			sequenceTrackListTableModel.fireTimeSignatureChanged();
		}
		sequenceTrackListTableModel.fireTrackChanged(track);
		int lastIndex = track.size() - 1;
		int oldLastIndex = lastIndex + midiEvents.length;
		if(lastIndex < 0) lastIndex = 0;
		fireTableRowsDeleted(oldLastIndex, lastIndex);
	}
	/**
	 * 引数の選択内容が示すMIDIイベントを除去します。
	 * @param selectionModel 選択内容
	 */
	public void removeSelectedMidiEvents() {
		removeMidiEvents(getSelectedMidiEvents());
	}
}
