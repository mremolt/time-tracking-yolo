import { Component } from '@angular/core';
import { Page } from '../core/page';

/** Short user guide in DE and EN for employees, project leads and backoffice (I-05). Texts live in the translation files. */
@Component({
  selector: 'app-help',
  template: `
  <article class="help card">
    <h1>{{ t('help.title') }}</h1>
    <p>{{ t('help.intro') }}</p>
    @for (s of sections; track s) {
      <section>
        <h2>{{ t('help.' + s + '.title') }}</h2>
        <ul>@for (i of items; track i) { @if (has('help.' + s + '.' + i)) { <li>{{ t('help.' + s + '.' + i) }}</li> } }</ul>
      </section>
    }
  </article>`
})
export class HelpPage extends Page {
  sections = ['employee', 'lead', 'backoffice', 'management'];
  items = [1, 2, 3, 4, 5, 6, 7, 8];
  has(k: string): boolean { return this.t(k) !== k; }
}
