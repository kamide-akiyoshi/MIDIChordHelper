package camidion.chordhelper.music;

import java.util.Vector;
import java.util.regex.Pattern;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Sequence;

/**
 * Chord Progression - コード進行のクラス
 */
public class ChordProgression {

	public class TickRange implements Cloneable {
		long start_tick_pos = 0, end_tick_pos = 0;
		public TickRange( long tick_pos ) {
			end_tick_pos = start_tick_pos = tick_pos;
		}
		public TickRange( long start_tick_pos, long end_tick_pos ) {
			this.start_tick_pos = start_tick_pos;
			this.end_tick_pos = end_tick_pos;
		}
		protected TickRange clone() {
			return new TickRange( start_tick_pos, end_tick_pos );
		}
		public void moveForward() {
			start_tick_pos = end_tick_pos;
		}
		public void moveForward( long duration ) {
			start_tick_pos = end_tick_pos;
			end_tick_pos += duration;
		}
		public long duration() {
			return end_tick_pos - start_tick_pos;
		}
		public boolean contains( long tick ) {
			return ( tick >= start_tick_pos && tick < end_tick_pos );
		}
	}

	class ChordStroke {
		Chord chord; int beat_length; TickRange tick_range = null;
		public ChordStroke(Chord chord) { this( chord, 1 ); }
		public ChordStroke(Chord chord, int beat_length) {
			this.chord = chord;
			this.beat_length = beat_length;
		}
		public String toString() {
			String str = chord.toString();
			for( int i=2; i <= beat_length; i++ ) str += " %";
			return str;
		}
	}

	// 時間位置付き歌詞
	public class Lyrics {
		String text = null;
		Long start_tick_pos = null;
		public Lyrics(String text) { this.text = text; }
		public Lyrics(String text, long tick_pos) {
			this.text = text; start_tick_pos = tick_pos;
		}
		public String toString() { return text; }
	}

	class Measure extends Vector<Object> {
		Long ticks_per_beat = null;
		public int numberOfBeats() {
			int n = 0;
			for( Object obj : this ) {
				if( obj instanceof ChordStroke ) {
					n += ((ChordStroke)obj).beat_length;
				}
			}
			return n;
		}
		// 小節内のコードストロークが時間的に等間隔かどうか調べる。
		// もし等間隔の場合、テキスト出力時に % をつける必要がなくなる。
		public boolean isEquallyDivided() {
			int l, l_prev = 0;
			for( Object obj : this ) {
				if( obj instanceof ChordStroke ) {
					l = ((ChordStroke)obj).beat_length;
					if( l_prev > 0 && l_prev != l ) {
						return false;
					}
					l_prev = l;
				}
			}
			return true;
		}
		public int addBeat() { return addBeat(1); }
		public int addBeat(int num_beats) {
			ChordStroke last_chord_stroke = null;
			for( Object obj : this ) {
				if( obj instanceof ChordStroke ) {
					last_chord_stroke = (ChordStroke)obj;
				}
			}
			if( last_chord_stroke == null ) {
				return 0;
			}
			return last_chord_stroke.beat_length += num_beats;
		}
		public String toString() {
			String str = "";
			boolean is_eq_dev = isEquallyDivided();
			for( Object element : this ) {
				str += " ";
				if( element instanceof ChordStroke ) {
					ChordStroke cs = (ChordStroke)element;
					str += is_eq_dev ? cs.chord : cs;
				}
				else if( element instanceof Lyrics ) {
					str += element.toString();
				}
			}
			return str;
		}
		public TickRange getRange() {
			long start_tick_pos = -1;
			long end_tick_pos = -1;
			for( Object element : this ) {
				if( ! (element instanceof ChordProgression.ChordStroke) )
					continue;
				ChordProgression.ChordStroke chord_stroke
				= (ChordProgression.ChordStroke)element;
				// 小節の先頭と末尾の tick を求める
				if( start_tick_pos < 0 ) {
					start_tick_pos = chord_stroke.tick_range.start_tick_pos;
				}
				end_tick_pos = chord_stroke.tick_range.end_tick_pos;
			}
			if( start_tick_pos < 0 || end_tick_pos < 0 ) {
				return null;
			}
			return new TickRange( start_tick_pos, end_tick_pos );
		}
		public ChordStroke chordStrokeAt( long tick ) {
			for( Object element : this ) {
				if( ! (element instanceof ChordProgression.ChordStroke) )
					continue;
				ChordProgression.ChordStroke chord_stroke
				= (ChordProgression.ChordStroke)element;
				if( chord_stroke.tick_range.contains(tick) ) {
					return chord_stroke;
				}
			}
			return null;
		}
	}
	class Line extends Vector<Measure> {
		public String toString() {
			String str = "";
			for( Measure measure : this ) str += measure + "|";
			return str;
		}
	}

	// 内部変数
	Vector<Line> lines = null;
	Key key = null;
	private Long ticks_per_measure = null;

	public Key getKey() { return key; }
	public void setKey(Key key) { this.key = key; }

	public String toString() {
		String str = "";
		if( key != null ) str += "Key: " + key + "\n";
		for( Line line : lines ) str += line + "\n";
		return str;
	}

	/**
	 * デフォルトの設定でコード進行を構築します。
	 */
	public ChordProgression() { }
	/**
	 * 指定された小節数、キー、拍子に合わせたコード進行を構築します。
	 * コード進行の内容は、ランダムに自動生成されます。
	 * @param measureLength 小節の長さ
	 * @param timeSignatureUpper 拍子の分子
	 */
	public ChordProgression( int measureLength, int timeSignatureUpper ) {
		int key_co5 = (int)(Math.random() * 12) - 5;
		key = new Key( key_co5, Key.MAJOR );
		lines = new Vector<Line>();
		Line line = new Line();
		boolean is_end;
		Chord chord, prev_chord = new Chord(new NoteSymbol(key_co5));
		int co5_offset, prev_co5_offset;
		double r;
		for( int mp=0; mp<measureLength; mp++ ) {
			is_end = (mp == 0 || mp == measureLength - 1); // 最初または最後の小節かを覚えておく
			Measure measure = new Measure();
			ChordStroke lastChordStroke = null;
			for( int i=0; i<timeSignatureUpper; i++ ) {
				if(
					i % 4 == 2 && Math.random() < 0.8
					||
					i % 2 != 0 && Math.random() < 0.9
				){
					// もう一拍延長
					lastChordStroke.beat_length++;
					continue;
				}
				chord = new Chord(new NoteSymbol(key_co5));
				co5_offset = 0;
				prev_co5_offset = prev_chord.rootNoteSymbol().toCo5() - key_co5;
				if( ! is_end ) {
					//
					// 最初または最後の小節は常にトニックにする。
					// 完全五度ずつ下がる進行を基本としつつ、時々そうでない進行も出現するようにする。
					// サブドミナントを超えるとスケールを外れるので、超えそうになったらランダムに決め直す。
					//
					r = Math.random();
					co5_offset = prev_co5_offset - 1;
					if( co5_offset < -1 || (prev_co5_offset < 5 && r < 0.5) ) {
						//
						// 長７度がルートとなるコードの出現確率を半減させながらコードを決める
						// （余りが６のときだけが長７度）
						// なお、前回と同じコードは使わないようにする。
						do {
							co5_offset = (int)(Math.random() * 13) % 7 - 1;
						} while( co5_offset == prev_co5_offset );
					}
					int co5RootNote = key_co5 + co5_offset;
					chord.setRoot(new NoteSymbol(co5RootNote));
					chord.setBass(new NoteSymbol(co5RootNote));
				}
				switch( co5_offset ) {
				// ルート音ごとに、7th などの付加や、メジャーマイナー反転を行う確率を決める
				case 5: // VII
					if( Math.random() < 0.5 ) {
						// m7-5
						chord.set(Chord.Interval.MINOR);
						chord.set(Chord.Interval.FLAT5);
					}
					if( Math.random() < 0.8 )
						chord.set(Chord.Interval.SEVENTH);
					break;
				case 4: // Secondary dominant (III)
					if( prev_co5_offset == 5 ) {
						// ルートが長７度→長３度の進行のとき、反転確率を上げる。
						// （ハ長調でいう Bm7-5 の次に E7 を出現しやすくする）
						if( Math.random() < 0.2 ) chord.set(Chord.Interval.MINOR);
					}
					else {
						if( Math.random() < 0.8 ) chord.set(Chord.Interval.MINOR);
					}
					if( Math.random() < 0.7 ) chord.set(Chord.Interval.SEVENTH);
					break;
				case 3: // VI
					if( Math.random() < 0.8 ) chord.set(Chord.Interval.MINOR);
					if( Math.random() < 0.7 ) chord.set(Chord.Interval.SEVENTH);
					break;
				case 2: // II
					if( Math.random() < 0.8 ) chord.set(Chord.Interval.MINOR);
					if( Math.random() < 0.7 ) chord.set(Chord.Interval.SEVENTH);
					break;
				case 1: // Dominant (V)
					if( Math.random() < 0.1 ) chord.set(Chord.Interval.MINOR);
					if( Math.random() < 0.3 ) chord.set(Chord.Interval.SEVENTH);
					if( Math.random() < 0.2 ) chord.set(Chord.Interval.NINTH);
					break;
				case 0: // Tonic（ここでマイナーで終わるとさみしいので setMinorThird() はしない）
					if( Math.random() < 0.2 ) chord.set(Chord.Interval.MAJOR_SEVENTH);
					if( Math.random() < 0.2 ) chord.set(Chord.Interval.NINTH);
					break;
				case -1: // Sub-dominant (IV)
					if( Math.random() < 0.1 ) {
						chord.set(Chord.Interval.MINOR);
						if( Math.random() < 0.3 ) chord.set(Chord.Interval.SEVENTH);
					}
					else
						if( Math.random() < 0.2 ) chord.set(Chord.Interval.MAJOR_SEVENTH);
					if( Math.random() < 0.2 ) chord.set(Chord.Interval.NINTH);
					break;
				}
				measure.add( lastChordStroke = new ChordStroke(chord) );
				prev_chord = chord;
			}
			line.add(measure);
			if( (mp+1) % 8 == 0 ) { // ８小節おきに改行
				lines.add(line);
				line = new Line();
			}
		}
		if( line.size() > 0 ) lines.add(line);
	}
	// テキストからコード進行を生成
	public ChordProgression( String source_text ) {
		if( source_text == null ) return;
		Measure measure;
		Line line;
		String[] lines_src, measures_src, elements_src;
		Chord last_chord = null;
		String key_regex = "^Key(\\s*):(\\s*)";
		//
		// キーであるかどうか見分けるためのパターン
		Pattern key_match_pattern = Pattern.compile(
				key_regex+".*$", Pattern.CASE_INSENSITIVE
				);
		// キーのヘッダーを取り除くためのパターン
		Pattern key_repl_pattern = Pattern.compile(
				key_regex, Pattern.CASE_INSENSITIVE
				);
		//
		lines_src = source_text.split("[\r\n]+");
		lines = new Vector<Line>();
		for( String line_src : lines_src ) {
			measures_src = line_src.split("\\|");
			if( measures_src.length > 0 ) {
				String key_string = measures_src[0].trim();
				if( key_match_pattern.matcher(key_string).matches() ) {
					key = new Key(
							key_repl_pattern.matcher(key_string).replaceFirst("")
							);
					// System.out.println("Key = " + key);
					continue;
				}
			}
			line = new Line();
			for( String measure_src : measures_src ) {
				elements_src = measure_src.split("[ \t]+");
				measure = new Measure();
				for( String element_src : elements_src ) {
					if( element_src.isEmpty() ) continue;
					if( element_src.equals("%") ) {
						if( measure.addBeat() == 0 ) {
							measure.add( new ChordStroke(last_chord) );
						}
						continue;
					}
					try {
						measure.add( new ChordStroke(
								last_chord = new Chord(element_src)
								));
					} catch( IllegalArgumentException ex ) {
						measure.add( new Lyrics(element_src) );
					}
				}
				line.add(measure);
			}
			lines.add(line);
		}
	}

	// Major/minor 切り替え
	public void toggleKeyMajorMinor() {
		key = key.relativeKey();
	}

	// コード進行の移調
	public void transpose(int chromatic_offset) {
		for( Line line : lines ) {
			for( Measure measure : line ) {
				for( int i=0; i<measure.size(); i++ ) {
					Object element = measure.get(i);
					if( element instanceof ChordStroke ) {
						ChordStroke cs = (ChordStroke)element;
						Chord new_chord = cs.chord.clone();
						//
						// キーが未設定のときは、最初のコードから推測して設定
						if( key == null ) key = new Key( new_chord );
						//
						new_chord.transpose( chromatic_offset, key );
						measure.set( i, new ChordStroke( new_chord, cs.beat_length ) );
					}
				}
			}
		}
		key.transpose(chromatic_offset);
	}
	// 異名同音の♭と＃を切り替える
	public void toggleEnharmonically() {
		if( key == null ) return;
		int original_key_co5 = key.toCo5();
		int co5Offset = 0;
		if( original_key_co5 > 4 ) {
			co5Offset = -Music.SEMITONES_PER_OCTAVE;
		}
		else if( original_key_co5 < -4 ) {
			co5Offset = Music.SEMITONES_PER_OCTAVE;
		}
		else {
			return;
		}
		key.toggleEnharmonically();
		for( Line line : lines ) {
			for( Measure measure : line ) {
				for( int i=0; i<measure.size(); i++ ) {
					Object element = measure.get(i);
					if( element instanceof ChordStroke ) {
						ChordStroke cs = (ChordStroke)element;
						Chord newChord = cs.chord.clone();
						newChord.setRoot(new NoteSymbol(newChord.rootNoteSymbol().toCo5() + co5Offset));
						newChord.setBass(new NoteSymbol(newChord.bassNoteSymbol().toCo5() + co5Offset));
						measure.set( i, new ChordStroke( newChord, cs.beat_length ) );
					}
				}
			}
		}
	}
	// コード進行の中に時間軸（MIDI tick）を書き込む
	//
	public void setTickPositions( FirstTrackSpec first_track ) {
		ticks_per_measure = first_track.ticks_per_measure;
		TickRange tick_range = new TickRange(
				first_track.pre_measures * ticks_per_measure
				);
		for( Line line : lines ) { // 行単位の処理
			for( Measure measure : line ) { // 小節単位の処理
				int n_beats = measure.numberOfBeats();
				if( n_beats == 0 ) continue;
				long tpb = measure.ticks_per_beat = ticks_per_measure / n_beats ;
				for( Object element : measure ) {
					if( element instanceof Lyrics ) {
						((Lyrics)element).start_tick_pos = tick_range.start_tick_pos;
						continue;
					}
					else if( element instanceof ChordStroke ) {
						ChordStroke chord_stroke = (ChordStroke)element;
						tick_range.moveForward( tpb * chord_stroke.beat_length );
						chord_stroke.tick_range = tick_range.clone();
					}
				}
			}
		}
	}
	// コード文字列の書き込み
	public void setChordSymbolTextTo( AbstractTrackSpec ts ) {
		for( Line line : lines ) {
			for( Measure measure : line ) {
				if( measure.ticks_per_beat == null ) continue;
				for( Object element : measure ) {
					if( element instanceof ChordStroke ) {
						ts.addStringTo( 0x01, (ChordStroke)element );
					}
				}
			}
		}
	}
	// 歌詞の書き込み
	public void setLyricsTo( AbstractTrackSpec ts ) {
		for( Line line : lines ) {
			for( Measure measure : line ) {
				if( measure.ticks_per_beat == null ) continue;
				for( Object element : measure ) {
					if( element instanceof Lyrics ) {
						ts.addStringTo( 0x05, (Lyrics)element );
					}
				}
			}
		}
	}
	/**
	 * コード進行をもとに MIDI シーケンスを生成します。
	 * @return MIDIシーケンス
	 */
	public Sequence toMidiSequence() {
		return toMidiSequence(48);
	}
	/**
	 * 指定のタイミング解像度で、
	 * コード進行をもとに MIDI シーケンスを生成します。
	 * @return MIDIシーケンス
	 */
	public Sequence toMidiSequence(int ppq) {
		//
		// PPQ = Pulse Per Quarter (TPQN = Tick Per Quearter Note)
		//
		return toMidiSequence( ppq, 0, 0, null, null );
	}
	/**
	 * 小節数、トラック仕様、コード進行をもとに MIDI シーケンスを生成します。
	 * @return MIDIシーケンス
	 */
	public Sequence toMidiSequence(
		int ppq, int start_measure_pos, int end_measure_pos,
		FirstTrackSpec first_track,
		Vector<AbstractNoteTrackSpec> track_specs
	) {
		Sequence seq;
		try {
			seq = new Sequence(Sequence.PPQ, ppq);
		} catch ( InvalidMidiDataException e ) {
			e.printStackTrace();
			return null;
		}
		// マスタートラックの生成
		if( first_track == null ) {
			first_track = new FirstTrackSpec();
		}
		first_track.key = this.key;
		first_track.createTrack( seq, start_measure_pos, end_measure_pos );
		//
		// 中身がなければここで終了
		if( lines == null || track_specs == null ) return seq;
		//
		// コード進行の中に時間軸（MIDI tick）を書き込む
		setTickPositions( first_track );
		//
		// コードのテキストと歌詞を書き込む
		setChordSymbolTextTo( first_track );
		setLyricsTo( first_track );
		//
		// 残りのトラックを生成
		for( AbstractNoteTrackSpec ts : track_specs ) {
			ts.createTrack( seq, first_track );
			if( ts instanceof DrumTrackSpec ) {
				((DrumTrackSpec)ts).addDrums(this);
			}
			else {
				((MelodyTrackSpec)ts).addChords(this);
			}
		}
		return seq;
	}
}