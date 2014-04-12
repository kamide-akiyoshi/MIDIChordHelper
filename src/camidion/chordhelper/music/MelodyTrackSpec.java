package camidion.chordhelper.music;



/**
 * メロディトラック仕様
 */
public class MelodyTrackSpec extends AbstractNoteTrackSpec {
	/**
	 * 音域
	 */
	public Range range;
	/**
	 * 音を出すかどうかを表すビット列
	 */
	public int beatPattern = 0xFFFF;
	/**
	 * あとで音を出し続けるかどうかを表すビット列
	 */
	public int continuousBeatPattern = 0xEEEE;
	/**
	 * ベース音を使う場合 true、それ以外のコード構成音を使う場合 false
	 */
	public boolean isBass = false;
	/**
	 * 乱数メロディを作るかどうか
	 */
	public boolean randomMelody = false;
	/**
	 * 乱数歌詞を作るかどうか
	 */
	public boolean randomLyric = false;
	/**
	 * 乱数歌詞をNSX-39（ポケット・ミク）対応の
	 * システムエクスクルーシブを使って出力するかどうか
	 */
	public boolean nsx39 = false;
	/**
	 * メロディトラック仕様を構築
	 * @param ch MIDIチャンネル
	 * @param name トラック名
	 */
	public MelodyTrackSpec(int ch, String name) {
		super(ch,name);
		range = new Range(
			Music.SEMITONES_PER_OCTAVE * 5, Music.SEMITONES_PER_OCTAVE * 6 );
	}
	/**
	 * 音域を指定してメロディトラック仕様を構築
	 * @param ch MIDIチャンネル
	 * @param name トラック名
	 * @param range 音域
	 */
	public MelodyTrackSpec(int ch, String name, Range range) {
		super(ch,name);
		this.range = range;
	}
	/**
	 * コードの追加
	 * @param cp コード進行
	 */
	public void addChords( ChordProgression cp ) {
		int mask;
		long tick;
		long startTickPos;

		// 音階ごとの生起確率を決める重みリスト（random_melody の場合）
		int i, noteNumber, prevNoteNumber = 1;
		int noteWeights[] = new int[range.max_note - range.min_note];
		//
		Key key = cp.key;
		if( key == null ) key = new Key("C");

		for( ChordProgression.Line line : cp.lines ) { // 行単位の処理
			for( ChordProgression.Measure measure : line ) { // 小節単位の処理
				if( measure.ticks_per_beat == null )
					continue;
				ChordProgression.TickRange tickRange = measure.getRange();
				boolean isNoteOn = false;
				//
				// 各ビートごとに繰り返し
				for(
					tick = startTickPos = tickRange.start_tick_pos, mask = 0x8000;
					tick < tickRange.end_tick_pos;
					tick += minNoteTicks, mask >>>= 1
				) {
					// そのtick地点のコードを調べる
					Chord chord = measure.chordStrokeAt(tick).chord;
					int notes[] = chord.toNoteArray(range);
					//
					// 各音階ごとに繰り返し
					if( Math.random() < 0.9 ) {
						if( (beatPattern & mask) == 0 ) {
							// 音を出さない
							continue;
						}
					}
					else {
						// ランダムに逆パターン
						if( (beatPattern & mask) != 0 ) {
							continue;
						}
					}
					if( ! isNoteOn ) {
						// 前回のビートで継続していなかったので、
						// この地点で音を出し始めることにする。
						startTickPos = tick;
						isNoteOn = true;
					}
					if( Math.random() < 0.9 ) {
						if( (continuousBeatPattern & mask) != 0 ) {
							// 音を継続
							continue;
						}
					}
					else {
						// ランダムに逆パターン
						if( (continuousBeatPattern & mask) == 0 ) {
							continue;
						}
					}
					// このビートの終了tickで音を出し終わることにする。
					if( randomMelody ) {
						// 音階ごとに出現確率を決める
						int totalWeight = 0;
						for( i=0; i<noteWeights.length; i++ ) {
							noteNumber = range.min_note + i;
							int m12 = Music.mod12(noteNumber - chord.rootNoteSymbol().toNoteNumber());
							int w;
							if( chord.indexOf(noteNumber) >= 0 ) {
								// コード構成音は確率を上げる
								w = 255;
							}
							else {
								switch( m12 ) {
								case 2: // 長２度
								case 9: // 長６度
									w = 63; break;
								case 5: // 完全４度
								case 11: // 長７度
									w = 47; break;
								default:
									w = 0; break;
								}
								if( ! key.isOnScale( noteNumber ) ) {
									// スケールを外れている音は採用しない
									w = 0;
								}
							}
							// 乱高下軽減のため、前回との差によって確率を調整する
							int diff = noteNumber - prevNoteNumber;
							if( diff < 0 ) diff = -diff;
							if( diff == 0 ) w /= 8;
							else if( diff > 7 ) w = 0;
							else if( diff > 4 ) w /= 8;
							totalWeight += (noteWeights[i] = w);
						}
						// さいころを振って音階を決定
						noteNumber = range.invertedNoteOf(key.rootNoteNumber());
						double r = Math.random();
						totalWeight *= r;
						for( i=0; i<noteWeights.length; i++ ) {
							if( (totalWeight -= noteWeights[i]) < 0 ) {
								noteNumber = range.min_note + i;
								break;
							}
						}
						if( randomLyric ) {
							// ランダムな歌詞を作成
							int index = (int)(Math.random() * MIDISpec.nsx39LyricElements.length);
							if( nsx39 ) {
								// ポケット・ミク用システムエクスクルーシブ
								byte nsx39SysEx[] = {
									0x43, 0x79, 0x09, 0x11, 0x0A, 0x00, (byte)(index & 0x7F), (byte) 0xF7
								};
								addSysEx(nsx39SysEx, startTickPos);
							}
							// 決定された音符を追加
							addNote(
								startTickPos, tick + minNoteTicks,
								noteNumber, velocity
							);
							// 歌詞をテキストとして追加
							addStringTo(0x05, MIDISpec.nsx39LyricElements[index], startTickPos);
						}
						else {
							// 決定された音符を追加
							addNote(
								startTickPos, tick + minNoteTicks,
								noteNumber, velocity
							);
						}
						prevNoteNumber = noteNumber;
					}
					else if( isBass ) {
						// ベース音を追加
						int note = range.invertedNoteOf(chord.bassNoteSymbol().toNoteNumber());
						addNote(
							startTickPos, tick + minNoteTicks,
							note, velocity
						);
					}
					else {
						// コード本体の音を追加
						for( int note : notes ) {
							addNote(
								startTickPos, tick + minNoteTicks,
								note, velocity
							);
						}
					}
					isNoteOn = false;
				}
			}
		}

	}

}