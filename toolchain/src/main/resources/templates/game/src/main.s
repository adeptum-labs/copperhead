; @NAME@ - a ship on a joystick
;
; The boot loader starts this program at LOAD while Kickstart still runs. The program takes the machine
; over at once and then, once every frame, moves sprite 0 with the joystick in port 2. It never hands
; the machine back, so the game runs until the machine is reset.

CUSTOM		=	$dff000
JOY1DAT		=	$00c
INTREQR		=	$01e
COP1LC		=	$080
DIWSTRT		=	$08e
DIWSTOP		=	$090
DDFSTRT		=	$092
DDFSTOP		=	$094
DMACON		=	$096
INTENA		=	$09a
INTREQ		=	$09c
BPL1PTH		=	$0e0
BPLCON0		=	$100
BPLCON1		=	$102
BPLCON2		=	$104
BPLCON3		=	$106
BPL1MOD		=	$108
BPLCON4		=	$10c
SPR0PTH		=	$120
COLOR00		=	$180
COLOR17		=	$1a2
DIWHIGH		=	$1e4
FMODE		=	$1fc

ALL		=	$7fff			; every bit but set/clear, which stays clear
VERTB		=	5			; the vertical blank bit of the interrupt registers
DMA_PLAY	=	$83a0			; set the master, bitplane, copper and sprite bits
ONE_PLANE	=	$1200			; colour output on, one bitplane
PALETTE_BANKS	=	$0c00			; colour bank 0, playfield 2 colours from 8: the older chips' way
SPRITE_BANKS	=	$0011			; sprites take colours 16 to 31, as on the older chips

FIRST_LINE	=	$2c
FIRST_COLUMN	=	$81
WIDTH		=	320
HEIGHT		=	200			; keeps the sprite's last line within the 8 bits of its control word
ROW		=	WIDTH/8			; bytes in a line of the bitplane
SIZE		=	16			; the ship is 16 by 16 pixels
STEP		=	2			; pixels the ship moves in a frame
; Bit 8 of the window's last column and bits 10 to 8 of its last line, then the same of its first.
WINDOW_HIGH	=	((((FIRST_COLUMN+WIDTH)>>8)&1)<<13)|((((FIRST_LINE+HEIGHT)>>8)&7)<<8)|(((FIRST_COLUMN>>8)&1)<<5)|((FIRST_LINE>>8)&7)

; Clamps the word in register \1 to the range \2 to \3.
clamp	macro
	cmp.w	#\2,\1
	bge	.above\@
	move.w	#\2,\1
.above\@:
	cmp.w	#\3,\1
	ble	.below\@
	move.w	#\3,\1
.below\@:
	endm

	org	LOAD

	lea	CUSTOM,a5
	move.w	#ALL,INTENA(a5)		; nothing of Kickstart's may interrupt from here on
	move.w	#ALL,INTREQ(a5)
	move.w	#ALL,DMACON(a5)
	; An AGA machine may have left wide fetches, another colour bank or other window bits on. These set
	; what the older chips do by themselves, and older chips ignore the writes.
	move.w	#0,FMODE(a5)
	move.w	#PALETTE_BANKS,BPLCON3(a5)
	move.w	#SPRITE_BANKS,BPLCON4(a5)
	move.w	#WINDOW_HIGH,DIWHIGH(a5)
	move.l	#copper,COP1LC(a5)	; the copper starts it at the next vertical blank
	move.w	#(WIDTH-SIZE)/2,d2	; x, from the left of the playfield
	move.w	#(HEIGHT-SIZE)/2,d3	; y, from its top
	bsr	place
	move.w	#DMA_PLAY,DMACON(a5)

main:
	bsr	wait_vblank
	bsr	steer
	bsr	place
	bra	main

; Waits for the next vertical blank. Sprite DMA reads the control words later in the frame, so a
; position written now shows in this frame.
wait_vblank:
	move.w	#1<<VERTB,INTREQ(a5)	; forget a blank that has already passed
.wait:
	btst	#VERTB,INTREQR+1(a5)
	beq	.wait
	rts

; Moves the ship by the joystick in port 2. Left and right are bits 9 and 1; up and down are those
; bits exclusive-or the bits next to them, 8 and 0.
steer:
	move.w	JOY1DAT(a5),d0
	move.w	d0,d1
	lsr.w	#1,d1
	eor.w	d0,d1
	btst	#9,d0
	beq	.right
	subq.w	#STEP,d2
.right:
	btst	#1,d0
	beq	.up
	addq.w	#STEP,d2
.up:
	btst	#8,d1
	beq	.down
	subq.w	#STEP,d3
.down:
	btst	#0,d1
	beq	.keep
	addq.w	#STEP,d3
.keep:
	clamp	d2,0,WIDTH-SIZE
	clamp	d3,0,HEIGHT-SIZE
	rts

; Writes the position into the sprite's control words: the first holds the start line and bits 8 to 1
; of the start column, the second the stop line and bit 0 of the column.
place:
	move.w	d3,d0
	add.w	#FIRST_LINE,d0
	move.w	d0,d1
	add.w	#SIZE,d1
	lsl.w	#8,d0
	lsl.w	#8,d1
	move.w	d2,d4
	add.w	#FIRST_COLUMN,d4
	move.w	d4,d5
	lsr.w	#1,d5
	or.w	d5,d0
	and.w	#1,d4
	or.w	d4,d1
	move.w	d0,sprite
	move.w	d1,sprite+2
	rts

; The copper restarts this list every frame, which also points the sprites back at their data: the
; hardware moves a sprite's pointer on as it reads.
copper:
	dc.w	DIWSTRT,(FIRST_LINE<<8)|FIRST_COLUMN
	dc.w	DIWSTOP,(((FIRST_LINE+HEIGHT)&$ff)<<8)|((FIRST_COLUMN+WIDTH)&$ff)
	dc.w	DDFSTRT,$0038
	dc.w	DDFSTOP,$00d0
	dc.w	BPLCON0,ONE_PLANE	; sprites show only where a bitplane is being displayed
	dc.w	BPLCON1,0
	dc.w	BPLCON2,0
	dc.w	BPL1MOD,-ROW		; every line shows the same empty row, so the plane needs one row
	dc.w	BPL1PTH,plane>>16
	dc.w	BPL1PTH+2,plane&$ffff
	dc.w	COLOR00,$004
	dc.w	COLOR17,$0ff
	dc.w	COLOR17+2,$f60
	dc.w	COLOR17+4,$fff
	dc.w	SPR0PTH,sprite>>16
	dc.w	SPR0PTH+2,sprite&$ffff
pointer	set	SPR0PTH+4
	rept	7
	dc.w	pointer,nothing>>16	; the other sprites show nothing, not what Kickstart left
	dc.w	pointer+2,nothing&$ffff
pointer	set	pointer+4
	endr
	dc.w	$ffff,$fffe		; a wait for a line that never comes ends the list

nothing:
	dc.w	0,0
plane:
	ds.b	ROW

	include	"sprite.i"
