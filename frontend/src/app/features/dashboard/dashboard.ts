import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-dashboard',
  imports: [MatCardModule],
  template: `
    <h1>Gösterge Paneli</h1>
    <mat-card appearance="outlined">
      <mat-card-content>
        Proje iskeleti hazır. Modüller sonraki fazlarda eklenecek.
      </mat-card-content>
    </mat-card>
  `,
})
export class Dashboard {}
