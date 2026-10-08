/*
    Copperhead
    Copyright © 2026 Adam Waldenberg and Adeptum AB

    This program is free software: you can redistribute it and/or modify it under the terms of the
    GNU General Public License as published by the Free Software Foundation, version 3 of the License.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
    even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along with this program.
    If not, see <http://www.gnu.org/licenses/>.
 */

package org.copperhead.core.sample;

import java.util.stream.IntStream;
import org.copperhead.core.project.SourceFile;
import org.copperhead.core.project.SourceLine;

/** The assembly the sample projects open with. */
final class SampleSources {
	private SampleSources() {
	}

	static SourceFile plasma() {
		return source("plasma.s",
			row("", "section", "code,code_c", ""),
			row("start:", "", "", ""),
			row("", "move.l", "4.w,a6", "; ExecBase"),
			row("", "lea", "$dff000,a5", "; custom chips"),
			row("", "move.w", "#$7fff,$9a(a5)", "; INTENA: all off"),
			row("", "move.w", "#$0020,$96(a5)", "; DMACON: sprites off"),
			row("", "lea", "copper(pc),a0", ""),
			row("", "move.l", "a0,$80(a5)", "; COP1LC"),
			row("", "clr.w", "$88(a5)", "; COPJMP1 - go"),
			row(".vbl:", "", "", ""),
			row("", "btst", "#0,$005(a5)", "; VPOSR bit 0"),
			row("", "bne.s", ".vbl", ""),
			row("", "bsr", "plasma_frame", ""),
			row("", "bra.s", ".vbl", ""),
			row("", "", "", ""),
			row("plasma_frame:", "", "", ""),
			row("", "lea", "sintab(pc),a0", ""),
			row("", "move.w", "frame(pc),d0", ""),
			row("", "addq.w", "#1,frame", ""),
			row("", "moveq", "#0,d1", ""),
			row(".row:", "move.b", "(a0,d0.w),d1", "; sin(x+t)"),
			row("", "add.b", "32(a0,d0.w),d1", ""),
			row("", "lsr.w", "#4,d1", "; 0..31 colour idx"),
			row("", "move.w", "(a1,d1.w*2),(a2)+", "; 020+ scale: OCS build warns"),
			row("", "addq.w", "#1,d0", ""),
			row("", "dbf", "d7,.row", ""),
			row("", "rts", "", ""),
			row("", "", "", ""),
			row("copper:", "dc.w", "$0100,$1200", "; BPLCON0 1 bpl"),
			row("", "dc.w", "$0180,$0000", "; COLOR00"),
			row("", "dc.w", "$3001,$fffe", "; WAIT $30"),
			row("", "dc.w", "$0180,$0a3f", ""),
			row("", "dc.w", "$ffff,$fffe", "; END"));
	}

	static SourceFile intro() {
		return source("intro.s",
			row("", "", "", "; intro.s - logo, 4 bitplanes, 16 colours"),
			row("intro_init:", "", "", ""),
			row("", "lea", "logo_pal(pc),a0", ""),
			row("", "lea", "$dff180,a1", "; COLOR00"),
			row("", "moveq", "#16-1,d0", ""),
			row(".pal:", "move.w", "(a0)+,(a1)+", ""),
			row("", "dbf", "d0,.pal", ""),
			row("", "move.w", "#$4200,$dff100", "; BPLCON0 4 bpl"),
			row("", "rts", "", ""),
			row("", "", "", ""),
			row("intro_frame:", "", "", ""),
			row("", "move.w", "fade(pc),d0", ""),
			row("", "beq.s", ".done", ""),
			row("", "subq.w", "#1,fade", ""),
			row("", "bsr", "fade_pal", "; 32 steps to black"),
			row(".done:", "rts", "", ""),
			row("", "", "", ""),
			row("fade:", "dc.w", "32", ""),
			row("logo_pal:", "incbin", "\"gfx/logo.pal\"", ""),
			row("logo:", "incbin", "\"gfx/logo.raw\"", "; 320x256x4 interleaved"));
	}

	static SourceFile main() {
		return source("main.s",
			row("", "", "", "; main.s - blitzball game loop, one pass per VBL"),
			row("game_loop:", "", "", ""),
			row("", "bsr", "wait_vbl", ""),
			row("", "bsr", "read_joy", "; JOY1DAT"),
			row("", "bsr", "move_player", ""),
			row("", "bsr", "move_ball", ""),
			row("", "bsr", "collide", "; ball vs bricks / paddle"),
			row("", "bsr", "blit_sprites", "; 16 hw sprites"),
			row("", "bsr", "play_music", "; ptplayer tick"),
			row("", "tst.w", "lives", ""),
			row("", "beq.s", "game_over", "; exit: Game over -> Title"),
			row("", "tst.w", "bricks_left", ""),
			row("", "beq.s", "level_clear", "; exit: Level clear -> next part"),
			row("", "bra.s", "game_loop", "; loop until an exit fires"),
			row("", "", "", ""),
			row("game_over:", "move.w", "#PART_TITLE,next_part", ""),
			row("", "rts", "", ""),
			row("level_clear:", "addq.w", "#1,level", ""),
			row("", "move.w", "#PART_NEXT,next_part", ""),
			row("", "rts", "", ""));
	}

	static SourceFile player() {
		return source("player.s",
			row("", "", "", "; player.s - paddle"),
			row("move_player:", "", "", ""),
			row("", "move.w", "$dff00c,d0", "; JOY1DAT"),
			row("", "btst", "#1,d0", "; right"),
			row("", "beq.s", ".l", ""),
			row("", "addq.w", "#2,paddle_x", ""),
			row(".l:", "btst", "#9,d0", "; left"),
			row("", "beq.s", ".done", ""),
			row("", "subq.w", "#2,paddle_x", ""),
			row(".done:", "rts", "", ""),
			row("paddle_x:", "dc.w", "144", ""));
	}

	static SourceFile copper() {
		return source("copper.i",
			row("", "", "", "; copper.i - shared copper macros"),
			row("CMOVE", "macro", "", ""),
			row("", "dc.w", "\\1,\\2", ""),
			row("", "endm", "", ""),
			row("CWAIT", "macro", "", ""),
			row("", "dc.w", "(\\1<<8)|$01,$fffe", ""),
			row("", "endm", "", ""),
			row("CEND", "macro", "", ""),
			row("", "dc.w", "$ffff,$fffe", ""),
			row("", "endm", "", ""),
			row("", "", "", ""),
			row("", "", "", "; bar: 64 lines, one COLOR00 change per line"),
			row("bar_start:", "", "", ""),
			row("", "CWAIT", "$30", ""),
			row("", "CMOVE", "$0180,$0000", ""),
			row("", "CWAIT", "$31", ""),
			row("", "CMOVE", "$0180,$0001", ""),
			row("", "CWAIT", "$32", ""),
			row("", "CMOVE", "$0180,$0013", ""),
			row("", "", "", "; ... generated by Copperhead palette tool"),
			row("", "CEND", "", ""));
	}

	private static String[] row(final String label, final String mnemonic, final String operands,
		final String comment) {

		return new String[] {label, mnemonic, operands, comment};
	}

	private static SourceFile source(final String name, final String[]... rows) {
		return new SourceFile(name, IntStream.range(0, rows.length)
			.mapToObj(i -> new SourceLine(i + 1, rows[i][0], rows[i][1], rows[i][2], rows[i][3])).toList());
	}
}
